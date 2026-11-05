package com.homebase;

import com.google.gson.Gson;
import com.homebase.party.HomebaseSyncMessage;
import com.homebase.sync.SyncRecord;
import com.homebase.sync.WireCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import static org.junit.Assert.assertEquals;

/**
 * Several members playing at once, all on one thread: stands in for RuneLite's party server and for time
 * passing, and leaves everything else to the real code. Each {@link SimulatedClient} runs the plugin's sync
 * and RuneLite's own party classes, and what they send each other goes through this class as the same bytes
 * they would send the real server.
 * <p>
 * The server isn't open source, so how it behaves here comes from what RuneLite's party client expects of
 * it: joining lists everyone already in the party, messages go to everyone including whoever sent them, and
 * a connection that closes can stay listed for a while before anyone is told it left.
 */
final class SimulatedParty
{
	static final long SECOND = 1_000;
	static final long MINUTE = 60 * SECOND;

	/**
	 * The smallest slice of time simulated.
	 */
	private static final long STEP_MS = 100;

	private static final class Delivery
	{
		final long at;
		final long order;
		final Connection to;
		final byte[] bytes;

		Delivery(long at, long order, Connection to, byte[] bytes)
		{
			this.at = at;
			this.order = order;
			this.to = to;
			this.bytes = bytes;
		}
	}

	/**
	 * Something a client sent the party, as the server saw it.
	 */
	static final class Sent
	{
		final String type;
		final int bytes;
		final String json;

		Sent(String type, int bytes, String json)
		{
			this.type = type;
			this.bytes = bytes;
			this.json = json;
		}
	}

	/**
	 * A client's connection to the server.
	 */
	private final class Connection implements WebSocket
	{
		final Request request;
		final WebSocketListener listener;
		long partyId;
		long memberId;
		boolean open = true;
		long lastDeliveryAt;

		Connection(Request request, WebSocketListener listener)
		{
			this.request = request;
			this.listener = listener;
		}

		@Override
		public Request request()
		{
			return request;
		}

		@Override
		public long queueSize()
		{
			return 0;
		}

		@Override
		public boolean send(String text)
		{
			throw new UnsupportedOperationException("the party only sends bytes");
		}

		@Override
		public boolean send(ByteString bytes)
		{
			if (open)
			{
				received(this, bytes.toByteArray());
			}
			return open;
		}

		@Override
		public boolean close(int code, String reason)
		{
			closed(this);
			return true;
		}

		@Override
		public void cancel()
		{
			closed(this);
		}
	}

	private final Gson gson = new Gson();
	private final Random random = new Random(20261105);
	private final Map<Long, List<Connection>> parties = new HashMap<>();
	private final PriorityQueue<Delivery> inFlight = new PriorityQueue<>(
		Comparator.<Delivery>comparingLong(d -> d.at).thenComparingLong(d -> d.order));
	private final List<SimulatedClient> clients = new ArrayList<>();
	private final Set<String> roster = new HashSet<>();
	private final List<Throwable> errors = new ArrayList<>();
	private final List<Sent> sent = new ArrayList<>();
	private final Map<Connection, Long> partsDue = new HashMap<>();
	private final String groupName = "HungryHorse";

	private long now = 1_790_000_000_000L;
	private long order;
	private long latencyMs;
	private long stayListedMs;

	/**
	 * @return a member of the group, logged in and not yet connected
	 */
	SimulatedClient member(String name)
	{
		roster.add(name);
		return addClient(name);
	}

	/**
	 * @return someone with the plugin who isn't in the group, logged in and not yet connected
	 */
	SimulatedClient outsider(String name)
	{
		return addClient(name);
	}

	private SimulatedClient addClient(String name)
	{
		SimulatedClient client = new SimulatedClient(this, name, 1_000L + clients.size(), random.nextLong());
		clients.add(client);
		return client;
	}

	String groupName()
	{
		return groupName;
	}

	/**
	 * @return the group's member list, as the game gives it
	 */
	Set<String> roster()
	{
		return roster;
	}

	boolean isOnRoster(String name)
	{
		for (String member : roster)
		{
			if (Text.toJagexName(member).equalsIgnoreCase(Text.toJagexName(name)))
			{
				return true;
			}
		}
		return false;
	}

	long now()
	{
		return now;
	}

	void noteError(Throwable error)
	{
		errors.add(error);
	}

	/**
	 * How long messages take to arrive, at most. Each connection still gets its messages in order.
	 */
	void setLatency(long latencyMs)
	{
		this.latencyMs = latencyMs;
	}

	/**
	 * How long a closed connection stays listed before the others are told it left.
	 */
	void setStayListed(long stayListedMs)
	{
		this.stayListedMs = stayListedMs;
	}

	/**
	 * Let time pass.
	 */
	void run(long millis)
	{
		for (long t = 0; t < millis; t += STEP_MS)
		{
			now += STEP_MS;
			removeDueParts();
			for (SimulatedClient client : clients)
			{
				client.step(STEP_MS);
			}
			settle();
		}
	}

	/**
	 * A long time goes by with nothing happening, such as weeks away from the game.
	 */
	void skip(long millis)
	{
		now += millis;
	}

	/**
	 * Everything that's due happens, until nothing more is.
	 */
	void settle()
	{
		boolean busy = true;
		while (busy)
		{
			busy = false;
			for (SimulatedClient client : clients)
			{
				busy |= client.runClientThread();
			}
			while (!inFlight.isEmpty() && inFlight.peek().at <= now)
			{
				Delivery delivery = inFlight.poll();
				if (delivery.to.open)
				{
					delivery.to.listener.onMessage(delivery.to, ByteString.of(delivery.bytes));
				}
				busy = true;
			}
		}
	}

	/**
	 * @return what every client has sent the party so far, as the server saw it
	 */
	List<Sent> sent()
	{
		return Collections.unmodifiableList(sent);
	}

	void forgetSent()
	{
		sent.clear();
	}

	/**
	 * @return each batch of records the clients have sent, as a receiver reads them
	 */
	List<List<SyncRecord>> recordBatches()
	{
		List<List<SyncRecord>> out = new ArrayList<>();
		for (String json : syncPayloads("R"))
		{
			out.add(gson.fromJson(json, WireCodec.RECORDS_TYPE));
		}
		return out;
	}

	/**
	 * @return the uncompressed text of every sync message of a type: "S" for a summary and "R" for records
	 */
	List<String> syncPayloads(String type)
	{
		List<String> out = new ArrayList<>();
		for (Sent message : sent)
		{
			if (!HomebaseSyncMessage.class.getSimpleName().equals(message.type))
			{
				continue;
			}
			HomebaseSyncMessage sync = gson.fromJson(message.json, HomebaseSyncMessage.class);
			if (type.equals(sync.getType()))
			{
				try
				{
					out.add(WireCodec.unpack(sync.getPayload()));
				}
				catch (IOException e)
				{
					throw new AssertionError(e);
				}
			}
		}
		return out;
	}

	void assertNoErrors()
	{
		if (!errors.isEmpty())
		{
			throw new AssertionError("Something threw while the clients ran: " + errors, errors.get(0));
		}
	}

	/**
	 * Every client named holds exactly the same records, deleted ones included.
	 */
	static void assertSameRecords(SimulatedClient first, SimulatedClient... others)
	{
		for (SimulatedClient other : others)
		{
			assertEquals(first.name() + " and " + other.name() + " should hold the same records",
				first.recordsAsText(), other.recordsAsText());
		}
	}

	/**
	 * A client's connection fails, as it does when the network drops.
	 */
	void loseConnection(WebSocket socket)
	{
		Connection connection = (Connection) socket;
		connection.listener.onFailure(connection, new IOException("connection lost"), null);
		closed(connection);
	}

	/**
	 * @return an HTTP client whose websockets lead here
	 */
	OkHttpClient http()
	{
		return new OkHttpClient()
		{
			@Override
			public WebSocket newWebSocket(Request request, WebSocketListener listener)
			{
				return new Connection(request, listener);
			}
		};
	}

	private void received(Connection from, byte[] bytes)
	{
		Object message = invokeStatic("C2S", "parseFrom", new Class<?>[]{byte[].class}, (Object) bytes);
		if ((boolean) read(message, "hasJoin"))
		{
			Object join = read(message, "getJoin");
			joined(from, (long) read(join, "getPartyId"), (long) read(join, "getMemberId"));
		}
		else if ((boolean) read(message, "hasPart"))
		{
			closed(from);
		}
		else if ((boolean) read(message, "hasData"))
		{
			shared(from, read(message, "getData"));
		}
		else
		{
			throw new AssertionError("A message with nothing in it");
		}
	}

	private void joined(Connection connection, long partyId, long memberId)
	{
		connection.partyId = partyId;
		connection.memberId = memberId;
		List<Connection> party = parties.computeIfAbsent(partyId, id -> new ArrayList<>());
		for (Connection member : new ArrayList<>(party))
		{
			if (member.memberId == memberId)
			{
				// the same member reconnecting replaces their old connection
				party.remove(member);
				partsDue.remove(member);
				member.open = false;
				continue;
			}
			deliver(connection, userMessage("setJoin", "UserJoin", partyId, member.memberId));
			if (member.open)
			{
				deliver(member, userMessage("setJoin", "UserJoin", partyId, memberId));
			}
		}
		party.add(connection);
		deliver(connection, userMessage("setJoin", "UserJoin", partyId, memberId));
	}

	private void shared(Connection from, Object data)
	{
		String type = (String) read(data, "getType");
		Object body = read(data, "getData");
		String text = ((com.google.protobuf.ByteString) body).toStringUtf8();
		sent.add(new Sent(type, (int) read(data, "getSerializedSize"), text));
		Object message = invokeStatic("PartyData", "newBuilder", new Class<?>[0]);
		set(message, "setPartyId", long.class, from.partyId);
		set(message, "setMemberId", long.class, from.memberId);
		set(message, "setType", String.class, type);
		set(message, "setData", com.google.protobuf.ByteString.class, body);
		byte[] bytes = serverMessage("setData", "PartyData", read(message, "build"));
		for (Connection member : parties.getOrDefault(from.partyId, Collections.emptyList()))
		{
			if (member.open)
			{
				deliver(member, bytes);
			}
		}
	}

	private void closed(Connection connection)
	{
		if (!connection.open)
		{
			return;
		}
		connection.open = false;
		if (stayListedMs > 0)
		{
			partsDue.put(connection, now + stayListedMs);
		}
		else
		{
			part(connection);
		}
	}

	private void removeDueParts()
	{
		for (Connection connection : new ArrayList<>(partsDue.keySet()))
		{
			if (partsDue.get(connection) <= now)
			{
				partsDue.remove(connection);
				part(connection);
			}
		}
	}

	private void part(Connection connection)
	{
		List<Connection> party = parties.get(connection.partyId);
		if (party == null || !party.remove(connection))
		{
			return;
		}
		byte[] bytes = userMessage("setPart", "UserPart", connection.partyId, connection.memberId);
		for (Connection member : party)
		{
			if (member.open)
			{
				deliver(member, bytes);
			}
		}
	}

	private void deliver(Connection to, byte[] bytes)
	{
		long at = latencyMs == 0 ? now : now + (long) (random.nextDouble() * latencyMs);
		at = Math.max(at, to.lastDeliveryAt);
		to.lastDeliveryAt = at;
		inFlight.add(new Delivery(at, order++, to, bytes));
	}

	private static byte[] userMessage(String setter, String type, long partyId, long memberId)
	{
		Object user = invokeStatic(type, "newBuilder", new Class<?>[0]);
		set(user, "setPartyId", long.class, partyId);
		set(user, "setMemberId", long.class, memberId);
		return serverMessage(setter, type, read(user, "build"));
	}

	private static byte[] serverMessage(String setter, String type, Object inner)
	{
		Object message = invokeStatic("S2C", "newBuilder", new Class<?>[0]);
		set(message, setter, messageClass(type), inner);
		return (byte[]) read(read(message, "build"), "toByteArray");
	}

	/*
	 * RuneLite's generated party messages sit inside a class that can't be named from outside its package,
	 * and nothing can join that package since RuneLite's jar is signed, so they're reached by name.
	 */

	private static Class<?> messageClass(String name)
	{
		try
		{
			return Class.forName("net.runelite.client.party.Party$" + name);
		}
		catch (ClassNotFoundException e)
		{
			throw new AssertionError(e);
		}
	}

	private static Object invokeStatic(String type, String method, Class<?>[] parameters, Object... arguments)
	{
		try
		{
			return messageClass(type).getMethod(method, parameters).invoke(null, arguments);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private static Object read(Object message, String method)
	{
		try
		{
			return message.getClass().getMethod(method).invoke(message);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private static void set(Object builder, String method, Class<?> parameter, Object value)
	{
		try
		{
			builder.getClass().getMethod(method, parameter).invoke(builder, value);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}
}
