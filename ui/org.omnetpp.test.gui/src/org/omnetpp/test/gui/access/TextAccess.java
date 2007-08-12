package org.omnetpp.test.gui.access;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Text;
import org.omnetpp.test.gui.InUIThread;

public class TextAccess extends ControlAccess<Text>
{
	public TextAccess(Text text) {
		super(text);
	}

	public Text getText() {
		return widget;
	}
	
	@InUIThread
	public String getTextContent() {
		return widget.getText();
	}

	@InUIThread
	public void clickAndType(String text) {
		click(); // focus
		pressKey(SWT.HOME);
		pressKey(SWT.END, SWT.SHIFT);  // select all
		typeKeySequence(text); // typing will replace content
	}
}
