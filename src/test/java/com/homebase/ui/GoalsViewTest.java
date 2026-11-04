package com.homebase.ui;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GoalsViewTest
{
	@Test
	public void aListGivesTheChoiceItShowsWithoutAnyoneTouchingIt()
	{
		JComboBox<String> box = new JComboBox<>(new String[]{"Attack", "Defence"});
		assertEquals("Attack", GoalsView.chosen(box));

		box.setModel(new DefaultComboBoxModel<>(new String[]{"Ardougne Easy", "Ardougne Medium"}));
		assertEquals("Ardougne Easy", GoalsView.chosen(box));
	}

	@Test
	public void aListYouCanTypeInGivesWhatWasTyped()
	{
		JComboBox<String> box = new JComboBox<>(new String[]{"Vorkath", "Zulrah"});
		box.setEditable(true);
		box.getEditor().setItem("Brutus");
		assertEquals("Brutus", GoalsView.chosen(box));
	}
}
