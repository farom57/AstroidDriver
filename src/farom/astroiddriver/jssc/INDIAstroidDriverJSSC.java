package farom.astroiddriver.jssc;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import farom.astroiddriver.CmdMessage;
import farom.astroiddriver.INDIAstroidDriver;
import farom.astroiddriver.StatusMessage;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;
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
public class INDIAstroidDriverJSSC extends INDIAstroidDriver implements SerialPortEventListener {

	private SerialPort serialPort;
	private INDITextProperty devicePortP; // DEVICE_PORT
	private INDITextElement devicePortE; // PORT
	
	/**
	 * @param inputStream
	 * @param outputStream
	 */
	public INDIAstroidDriverJSSC(InputStream inputStream, OutputStream outputStream) {
		super(inputStream, outputStream);
		serialPort = null;
		
		devicePortP = new INDITextProperty(this, "DEVICE_PORT", "Port", "Main Control", Constants.PropertyStates.IDLE,
				Constants.PropertyPermissions.RW);
		String[] portNames = SerialPortList.getPortNames();
		if (portNames.length > 0) {
			devicePortE = new INDITextElement(devicePortP, "PORT", portNames[0]);
			devicePortP.setState(PropertyStates.OK);
			addProperty(devicePortP, "Serial port found : " + portNames[0]);
			printMessage("Serial port found : " + portNames[0]);
		} else {
			devicePortE = new INDITextElement(devicePortP, "PORT", "/dev/tty???");
			devicePortP.setState(PropertyStates.ALERT);
			addProperty(devicePortP, "Serial port not found");
			printMessage("Serial port not found");
		}
		
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
		if (property == devicePortP) {
			if (elementsAndValues.length > 0) {
				devicePortE.setValue(elementsAndValues[0].getValue());
				devicePortP.setState(PropertyStates.OK);
			}

			try {
				updateProperty(devicePortP);
			} catch (INDIException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void driverConnect(Date timestamp) throws INDIException {
		printMessage("try to open " + devicePortE.getValue());
		try {
			serialPort = new SerialPort(devicePortE.getValue());
			serialPort.openPort();
			serialPort.setParams(9600, 8, 1, 0);
			serialPort.setEventsMask(SerialPort.MASK_RXCHAR);
			serialPort.addEventListener(this);

			onConnected();

		} catch (SerialPortException e) {
			printMessage("Connection failled");
			updateProperty(devicePortP, "Connection failled");
			throw new INDIException("SerialPortException : " + e.getMessage());
		}		
	}
	
	@Override
	public void driverDisconnect(Date timestamp) throws INDIException {
		try {
			serialPort.closePort();
			
			onDisconnected();
			
		} catch (SerialPortException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Send the current command message to the device
	 */
	@Override
	protected void sendCommand() {
		try {
			if (!serialPort.writeBytes(command.getBytes())) {
				printMessage("error while sending data to the device");
			}
			System.out.println("Sent command:");
			for (int i = 0; i < CmdMessage.MESSAGE_SIZE; i++) {
				byte[] array = command.getBytes();
				System.out.printf("%02X ", array[i]);
			}
			System.out.println("");
		} catch (SerialPortException e) {
			printMessage("error while sending data to the device");
			e.printStackTrace();
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see jssc.SerialPortEventListener#serialEvent(jssc.SerialPortEvent)
	 */
	@Override
	public void serialEvent(SerialPortEvent event) {
		if (event.isRXCHAR()) {// If data is available

			if (event.getEventValue() >= StatusMessage.MESSAGE_SIZE) {
				try {
					byte buffer[] = serialPort.readBytes(StatusMessage.MESSAGE_SIZE);
					serialPort.readBytes(event.getEventValue() - StatusMessage.MESSAGE_SIZE);

					if (StatusMessage.verify(buffer)) {
						
						lastStatusMessage = new StatusMessage(buffer);
//						System.out.println("valid message (" + event.getEventValue() + "/"
//								+ StatusMessage.MESSAGE_SIZE + " bytes):");
//						for (int i = 0; i < StatusMessage.MESSAGE_SIZE; i++) {
//							System.out.printf("%02X ", buffer[i]);
//						}
//						System.out.println("");
//						System.out.println(lastStatusMessage);
						
						
						updateStatus();
					} else {
						System.out.println("invalid message (" + event.getEventValue() + "/"
								+ StatusMessage.MESSAGE_SIZE + " bytes):");
						for (int i = 0; i < StatusMessage.MESSAGE_SIZE; i++) {
							System.out.printf("%02X ", buffer[i]);
						}
						System.out.println("");
					}
				} catch (SerialPortException e) {
					e.printStackTrace();
				}

			}

		}
	}


}
