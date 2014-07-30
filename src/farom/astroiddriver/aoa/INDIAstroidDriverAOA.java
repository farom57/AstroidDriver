package farom.astroiddriver.aoa;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import farom.astroiddriver.INDIAstroidDriver;
import farom.astroiddriver.StatusMessage;
import laazotea.indi.Constants;
import laazotea.indi.INDIException;
import laazotea.indi.Constants.PropertyStates;
import laazotea.indi.driver.INDITextElement;
import laazotea.indi.driver.INDITextElementAndValue;
import laazotea.indi.driver.INDITextProperty;

/**
 * @author farom
 *
 */
public class INDIAstroidDriverAOA extends INDIAstroidDriver{

	
	/**
	 * @param inputStream
	 * @param outputStream
	 */
	public INDIAstroidDriverAOA(InputStream inputStream, OutputStream outputStream) {
		super(inputStream, outputStream);
		
		
	}
	
	/**
	 * Called when a new Text Vector message has been received from a Client.
	 * 
	 * @param property
	 *            The Text Property asked to change.
	 * @param timestamp
	 *            The timestamp of the received message
	 * @param elementsAndValues
	 *            An array of pairs of Text Elements and its requested values to
	 *            be parsed.
	 */
	@Override
	public void processNewTextValue(INDITextProperty property, Date date, INDITextElementAndValue[] elementsAndValues) {
		super.processNewTextValue(property, date, elementsAndValues);
		
	}
	
	@Override
	public void driverConnect(Date timestamp) throws INDIException {
		

			onConnected();
		
	}
	
	@Override
	public void driverDisconnect(Date timestamp) throws INDIException {

			onDisconnected();

	}

	/**
	 * Send the current command message to the device
	 */
	@Override
	protected void sendCommand() {

	}
	
}