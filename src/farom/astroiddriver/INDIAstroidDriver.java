package farom.astroiddriver;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


import laazotea.indi.Constants;
import laazotea.indi.Constants.LightStates;
import laazotea.indi.Constants.PropertyStates;
import laazotea.indi.Constants.SwitchStatus;
import laazotea.indi.INDIException;
import laazotea.indi.driver.*;

/**
 * A class representing a INDI Driver for the Astroid device.
 * 
 * @author farom
 */
public abstract class INDIAstroidDriver extends INDIDriver implements INDIConnectionHandler {

	/**
	 * number of steps per turn
	 */
	private static final int STEP_BY_TURN = 50 * 3 * 144;

	/**
	 * if the motor direction is inverted
	 */
	private static final boolean INVERT_RA = true;

	/**
	 * if the motor direction is inverted
	 */
	private static final boolean INVERT_DE = false; //

	/**
	 * status turns red if no StatusMessage is received for
	 * CONNECTION_TIMEOUT_ALERT milliseconds
	 */
	private static final long CONNECTION_TIMEOUT_ALERT = 1000; // the link

	private static final double GOTO_STOP_DISTANCE = 1. / 60.;
	private static final double GOTO_SLOW_DISTANCE = 15. / 60.;
	private static final float MAX_SPEED = 240;
	private static final float GOTO_SPEED = MAX_SPEED;
	private static final float GOTO_SLOW_SPEED = GOTO_SPEED/10;

	/**
	 * sideral rate in arcmin/sec
	 */
	private static final double SIDERAL_RATE = 360. * 60. / 86164.09053;



	private INDINumberProperty geographicCoordP; // GEOGRAPHIC_COORD
	private INDINumberElement geographicCoordLatE; // LAT
	private INDINumberElement geographicCoordLongE; // LONG
	private INDINumberElement geographicCoordElevE; // ELEV

	private INDINumberProperty eqCoordP; // EQUATORIAL_EOD_COORD
	private INDINumberElement eqCoordRAE; // RA
	private INDINumberElement eqCoordDEE; // DEC

	private INDISwitchProperty onCoordSetP; // ON_COORD_SET
	private INDISwitchElement onCoordSetSlewE; // SLEW
	private INDISwitchElement onCoordSetTrackE; // TRACK
	private INDISwitchElement onCoordSetSyncE; // SYNC

	private INDISwitchProperty telescopeMotionNSP; // TELESCOPE_MOTION_NS
	private INDISwitchElement motionNE; // MOTION_NORTH
	private INDISwitchElement motionSE; // MOTION_SOUTH

	private INDISwitchProperty telescopeMotionWEP; // TELESCOPE_MOTION_WE
	private INDISwitchElement motionWE; // MOTION_WEST
	private INDISwitchElement motionEE; // MOTION_EAST

	private INDISwitchProperty abortMotionP; // TELESCOPE_ABORT_MOTION
	private INDISwitchElement abortMotionE; // ABORT_MOTION

	/**
	 * On German equatorial mounts, a given celestial position can be pointed in
	 * two ways. The counter-weight is generally down and the telescope up. -
	 * East means that the telescope is pointing toward the east when the
	 * counter-weight is down (and west when it is up). - West means that the
	 * telescope is pointing toward the west when the counter-weight is down
	 * (and east when it is up). When INVERT_DE = false and side = East, the
	 * declination increase when the DE speed is positive
	 */
	private INDISwitchProperty sideP; // side
	private INDISwitchElement sideEastE; // east
	private INDISwitchElement sideWestE; // west

	private INDINumberProperty timeLstP; // TIME_LST
	private INDINumberElement lstE; // LST

	// private INDISwitchProperty enableAxisP;
	// private INDISwitchElement enableDecE;
	// private INDISwitchElement enableRaE;
	//
	// private INDITextProperty commandP;
	// private INDITextElement commandE;

	private INDILightProperty linkStatusP;
	private INDILightElement linkStatusE;

	private INDINumberProperty motionRateP;
	private INDINumberElement motionRateE;

	
	protected StatusMessage lastStatusMessage;
	protected CmdMessage command;

	private double syncCoordHA;
	private double syncStepHA;
	private double syncCoordDE;
	private double syncStepDE;
	private double gotoTargetRA;
	private double gotoTargetDE;
	private boolean gotoActive;
	private float motionSpeed;

	/**
	 * Constructs a INDIAstroidDriver with a particular
	 * <code>inputStream<code> from which to read the incoming messages (from clients) and a
	 * <code>outputStream</code> to write the messages to the clients.
	 * 
	 * @param inputStream
	 *            The stream from which to read messages
	 * @param outputStream
	 *            The stream to which to write the messages
	 */
	public INDIAstroidDriver(InputStream inputStream, OutputStream outputStream) {
		super(inputStream, outputStream);
		

		// ------------------------------------------
		// --- Setup INDI properties and elements ---
		// ------------------------------------------

		linkStatusP = new INDILightProperty(this, "link_status", "Link status", "Main Control", PropertyStates.IDLE);
		linkStatusE = new INDILightElement(linkStatusP, "USB/Serial", LightStates.ALERT);

		geographicCoordP = new INDINumberProperty(this, "GEOGRAPHIC_COORD", "Scope Location", "Scope Location",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW); // GEOGRAPHIC_COORD
		geographicCoordLatE = new INDINumberElement(geographicCoordP, "LAT", "Lat (dd:mm:ss)", 0., -90, 90, 0.,
				"%010.6m");
		geographicCoordLongE = new INDINumberElement(geographicCoordP, "LONG", "Lon (dd:mm:ss)", 0., 0, 360, 0.,
				"%010.6m");
		geographicCoordElevE = new INDINumberElement(geographicCoordP, "ELEV", "Elevation (m)", 0., -1000, 100000, 0.,
				"%g");
		addProperty(geographicCoordP);

		timeLstP = new INDINumberProperty(this, "TIME_LST", "Local sidereal time", "Scope Location",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RO);
		lstE = new INDINumberElement(timeLstP, "LST", "Local sidereal time", 0, 0, 24, 0, "%010.6m");
		addProperty(timeLstP);

		eqCoordP = new INDINumberProperty(this, "EQUATORIAL_EOD_COORD", "Eq. Coordinates", "Main Control",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW); // EQUATORIAL_EOD_COORD
		eqCoordRAE = new INDINumberElement(eqCoordP, "RA", "RA (hh:mm:ss)", 0., 0, 24, 0, "%010.6m"); // RA
		eqCoordDEE = new INDINumberElement(eqCoordP, "DEC", "DEC (dd:mm:ss)", 0., -180, 180, 0, "%010.6m"); // DEC

		sideP = new INDISwitchProperty(this, "TELESCOPE_SIDE", "Telescope side", "Main Control",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW, Constants.SwitchRules.ONE_OF_MANY); // TELESCOPE_MOTION_WE
		sideEastE = new INDISwitchElement(sideP, "WEST", "West", Constants.SwitchStatus.OFF);
		sideWestE = new INDISwitchElement(sideP, "EAST", "East", Constants.SwitchStatus.ON);

		onCoordSetP = new INDISwitchProperty(this, "ON_COORD_SET", "On Set", "Main Control",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW, Constants.SwitchRules.ONE_OF_MANY); // ON_COORD_SET
		onCoordSetSlewE = new INDISwitchElement(onCoordSetP, "SLEW", "Slew", Constants.SwitchStatus.OFF); // SLEW
		onCoordSetTrackE = new INDISwitchElement(onCoordSetP, "TRACK", "Track", Constants.SwitchStatus.OFF); // TRACK
		onCoordSetSyncE = new INDISwitchElement(onCoordSetP, "SYNC", "Sync", Constants.SwitchStatus.ON); // SYNC

		// enableAxisP = new INDISwitchProperty(this, "ENABLE_AXIS",
		// "Enable axis", "Main Control",
		// Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW,
		// Constants.SwitchRules.ANY_OF_MANY);
		// enableDecE = new INDISwitchElement(enableAxisP, "DEC", "Dec",
		// Constants.SwitchStatus.ON);
		// enableRaE = new INDISwitchElement(enableAxisP, "RA", "Ra",
		// Constants.SwitchStatus.ON);
		// // addProperty(enableAxis);

		telescopeMotionNSP = new INDISwitchProperty(this, "TELESCOPE_MOTION_NS", "North/South", "Motion Control",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW, Constants.SwitchRules.AT_MOST_ONE); // TELESCOPE_MOTION_NS
		motionNE = new INDISwitchElement(telescopeMotionNSP, "MOTION_NORTH", "North", Constants.SwitchStatus.OFF); // MOTION_NORTH
		motionSE = new INDISwitchElement(telescopeMotionNSP, "MOTION_SOUTH", "South", Constants.SwitchStatus.OFF); // MOTION_SOUTH

		telescopeMotionWEP = new INDISwitchProperty(this, "TELESCOPE_MOTION_WE", "West/East", "Motion Control",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW, Constants.SwitchRules.AT_MOST_ONE); // TELESCOPE_MOTION_WE
		motionWE = new INDISwitchElement(telescopeMotionWEP, "MOTION_WEST", "West", Constants.SwitchStatus.OFF); // MOTION_WEST
		motionEE = new INDISwitchElement(telescopeMotionWEP, "MOTION_EAST", "East", Constants.SwitchStatus.OFF); // MOTION_EAST

		abortMotionP = new INDISwitchProperty(this, "TELESCOPE_ABORT_MOTION", "Abort Motion", "Motion Control",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW, Constants.SwitchRules.AT_MOST_ONE); // TELESCOPE_ABORT_MOTION
		abortMotionE = new INDISwitchElement(abortMotionP, "ABORT_MOTION", "Abort", Constants.SwitchStatus.OFF); // ABORT_MOTION

		motionRateP = new INDINumberProperty(this, "TELESCOPE_MOTION_RATE", "Motion rate", "Motion Control",
				Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW);
		motionRateE = new INDINumberElement(motionRateP, "MOTION_RATE", "Motion rate (arcmin/s)", MAX_SPEED*SIDERAL_RATE, 0., MAX_SPEED*SIDERAL_RATE, 0,
				"%7.2f");
		motionSpeed = (float) (motionRateE.getValue() / SIDERAL_RATE);

		// commandP = new INDITextProperty(this, "custom_command",
		// "Custom command", "Advanced",
		// Constants.PropertyStates.IDLE, Constants.PropertyPermissions.RW);
		// commandE = new INDITextElement(commandP, "command", "");

		// --- Remaining initializations ---

		lastStatusMessage = new StatusMessage();
		command = new CmdMessage();

		// --- Setup 1s timer (sidereal time update & link status verification)
		// ---
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				lstE.setValue(getSiderealTime());
				if (isConnected()) {
					if ((new Date()).getTime() - lastStatusMessage.time > CONNECTION_TIMEOUT_ALERT) {
						linkStatusE.setValue(LightStates.ALERT);
					} else {
						linkStatusE.setValue(LightStates.OK);
					}

					try {
						updateProperty(timeLstP);
						updateProperty(linkStatusP);
					} catch (INDIException e) {
						e.printStackTrace();
					}
				}
			}
		};
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(task, 0, 1000);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see laazotea.indi.driver.INDIDriver#getName()
	 */
	@Override
	public String getName() {
		return "INDI Astroid Driver";
	}

	/**
	 * Called when a new BLOB Vector message has been received from a Client.
	 * 
	 * @param property
	 *            The BLOB Property asked to change.
	 * @param timestamp
	 *            The timestamp of the received message
	 * @param elementsAndValues
	 *            An array of pairs of BLOL Elements and its requested values to
	 *            be parsed.
	 */
	@Override
	public void processNewBLOBValue(INDIBLOBProperty property, Date date, INDIBLOBElementAndValue[] elementsAndValues) {

	}

	/**
	 * Called when a new Number Vector message has been received from a Client.
	 * 
	 * @param property
	 *            The Number Property asked to change.
	 * @param timestamp
	 *            The timestamp of the received message
	 * @param elementsAndValues
	 *            An array of pairs of Number Elements and its requested values
	 *            to be parsed.
	 */
	@Override
	public void processNewNumberValue(INDINumberProperty property, Date date,
			INDINumberElementAndValue[] elementsAndValues) {

		// --- Geographic coordinates ---
		if (property == geographicCoordP) {
			for (int i = 0; i < elementsAndValues.length; i++) {
				INDINumberElement el = elementsAndValues[i].getElement();
				double val = elementsAndValues[i].getValue();
				if (el == geographicCoordLatE) {
					geographicCoordLatE.setValue(val);
				}
				if (el == geographicCoordLongE) {
					geographicCoordLongE.setValue(val);
				}
				if (el == geographicCoordElevE) {
					geographicCoordElevE.setValue(val);
				}
				geographicCoordP.setState(PropertyStates.OK);

			}
			try {
				updateProperty(geographicCoordP);
			} catch (INDIException e) {
				e.printStackTrace();
			}
		}

		// --- Equatorial coordinates ---
		if (property == eqCoordP) {
			double newRA = 0;
			double newDE = 0;
			eqCoordP.setState(PropertyStates.BUSY);
			for (int i = 0; i < elementsAndValues.length; i++) {
				INDINumberElement el = elementsAndValues[i].getElement();
				double val = elementsAndValues[i].getValue();
				if (el == eqCoordDEE) {
					newDE = mod360(val);
				} else {
					newRA = mod24(val);
				}
			}

			if (onCoordSetSyncE.getValue() == SwitchStatus.ON) {
				syncCoordinates(newRA, newDE);
			} else {
				gotoCoordinates(newRA, newDE);
			}

		}

		// --- Motion rate ---
		if (property == motionRateP) {
			double val = elementsAndValues[0].getValue();
			motionRateE.setValue(val);
			motionSpeed = (float) (val / SIDERAL_RATE);

			if (motionNE.getValue() == SwitchStatus.ON) {
				command.setSpeedDE(motionSpeed * (INVERT_DE ? -1 : 1)
						* (sideEastE.getValue() == SwitchStatus.ON ? 1 : -1));
				telescopeMotionNSP.setState(PropertyStates.OK);
			} else if (motionSE.getValue() == SwitchStatus.ON) {
				command.setSpeedDE(-motionSpeed * (INVERT_DE ? -1 : 1)
						* (sideEastE.getValue() == SwitchStatus.ON ? 1 : -1));
				telescopeMotionNSP.setState(PropertyStates.OK);
			}
			if (motionWE.getValue() == SwitchStatus.ON) {
				command.setSpeedRA(motionSpeed * (INVERT_RA ? -1 : 1));
				telescopeMotionWEP.setState(PropertyStates.OK);
			} else if (motionEE.getValue() == SwitchStatus.ON) {
				command.setSpeedRA(-motionSpeed * (INVERT_RA ? -1 : 1));
				telescopeMotionWEP.setState(PropertyStates.OK);
			}
			sendCommand();
			motionRateP.setState(PropertyStates.OK);

			try {
				updateProperty(motionRateP);
			} catch (INDIException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * Called when a new Switch Vector message has been received from a Client.
	 * 
	 * @param property
	 *            The Switch Property asked to change.
	 * @param timestamp
	 *            The timestamp of the received message
	 * @param elementsAndValues
	 *            An array of pairs of Switch Elements and its requested values
	 *            to be parsed.
	 */
	@Override
	public void processNewSwitchValue(INDISwitchProperty property, Date date,
			INDISwitchElementAndValue[] elementsAndValues) {

		if (property == onCoordSetP) {
			onCoordSetP.setState(PropertyStates.IDLE);
			for (int i = 0; i < elementsAndValues.length; i++) {
				INDISwitchElement el = elementsAndValues[i].getElement();
				SwitchStatus val = elementsAndValues[i].getValue();
				if (val == SwitchStatus.ON) {
					onCoordSetSlewE.setValue(SwitchStatus.OFF);
					onCoordSetTrackE.setValue(SwitchStatus.OFF);
					onCoordSetSyncE.setValue(SwitchStatus.OFF);
					el.setValue(SwitchStatus.ON);
					onCoordSetP.setState(PropertyStates.OK);

				}

			}
			try {
				updateProperty(onCoordSetP);
			} catch (INDIException e) {
				e.printStackTrace();
			}
		}

		if (property == sideP) {
			sideP.setState(PropertyStates.IDLE);
			boolean hasChanged = false;
			for (int i = 0; i < elementsAndValues.length; i++) {
				INDISwitchElement el = elementsAndValues[i].getElement();
				SwitchStatus val = elementsAndValues[i].getValue();
				if (val != el.getValue()) {
					hasChanged = true;
				}
			}
			if (hasChanged) {
				if (sideWestE.getValue() == SwitchStatus.ON) {
					sideWestE.setValue(SwitchStatus.OFF);
					sideEastE.setValue(SwitchStatus.ON);
				} else {
					sideWestE.setValue(SwitchStatus.ON);
					sideEastE.setValue(SwitchStatus.OFF);
				}
				sideP.setState(PropertyStates.OK);

				syncCoordHA = syncCoordHA + 12;
				syncCoordDE = 180 - syncCoordDE;
			}
			try {
				updateProperty(sideP);
			} catch (INDIException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (property == telescopeMotionNSP) {

			if (elementsAndValues.length != 2) {
				printMessage("elementsAndValues.length!=2");
				return;
			}

			if (motionNE.getValue() == SwitchStatus.ON) {
				if ((elementsAndValues[0].getElement() == motionSE && elementsAndValues[0].getValue() == SwitchStatus.ON)
						|| (elementsAndValues[1].getElement() == motionSE && elementsAndValues[1].getValue() == SwitchStatus.ON)) {
					motionNE.setValue(SwitchStatus.OFF);
					motionSE.setValue(SwitchStatus.ON);
				} else if ((elementsAndValues[0].getElement() == motionNE && elementsAndValues[0].getValue() == SwitchStatus.OFF)
						|| (elementsAndValues[1].getElement() == motionNE && elementsAndValues[1].getValue() == SwitchStatus.OFF)) {
					motionNE.setValue(SwitchStatus.OFF);
				}
			} else if (motionSE.getValue() == SwitchStatus.ON) {
				if ((elementsAndValues[0].getElement() == motionNE && elementsAndValues[0].getValue() == SwitchStatus.ON)
						|| (elementsAndValues[1].getElement() == motionNE && elementsAndValues[1].getValue() == SwitchStatus.ON)) {
					motionSE.setValue(SwitchStatus.OFF);
					motionNE.setValue(SwitchStatus.ON);
				} else if ((elementsAndValues[0].getElement() == motionSE && elementsAndValues[0].getValue() == SwitchStatus.OFF)
						|| (elementsAndValues[1].getElement() == motionSE && elementsAndValues[1].getValue() == SwitchStatus.OFF)) {
					motionSE.setValue(SwitchStatus.OFF);
				}
			} else {
				if (elementsAndValues[0].getValue() == SwitchStatus.ON) {
					elementsAndValues[0].getElement().setValue(SwitchStatus.ON);
				} else if (elementsAndValues[1].getValue() == SwitchStatus.ON) {
					elementsAndValues[1].getElement().setValue(SwitchStatus.ON);
				}
			}

			if (motionNE.getValue() == SwitchStatus.ON) {
				command.setSpeedDE(motionSpeed * (INVERT_DE ? -1 : 1)
						* (sideEastE.getValue() == SwitchStatus.ON ? 1 : -1));
				telescopeMotionNSP.setState(PropertyStates.OK);
			} else if (motionSE.getValue() == SwitchStatus.ON) {
				command.setSpeedDE(-motionSpeed * (INVERT_DE ? -1 : 1)
						* (sideEastE.getValue() == SwitchStatus.ON ? 1 : -1));
				telescopeMotionNSP.setState(PropertyStates.OK);
			} else {
				command.setSpeedDE(0);
				telescopeMotionNSP.setState(PropertyStates.IDLE);
			}
			sendCommand();

			try {
				updateProperty(telescopeMotionNSP);
			} catch (INDIException e) {
				e.printStackTrace();
			}
		}

		if (property == telescopeMotionWEP) {

			if (elementsAndValues.length != 2) {
				printMessage("elementsAndValues.length!=2");
				return;
			}

			if (motionWE.getValue() == SwitchStatus.ON) {
				if ((elementsAndValues[0].getElement() == motionEE && elementsAndValues[0].getValue() == SwitchStatus.ON)
						|| (elementsAndValues[1].getElement() == motionEE && elementsAndValues[1].getValue() == SwitchStatus.ON)) {
					motionWE.setValue(SwitchStatus.OFF);
					motionEE.setValue(SwitchStatus.ON);
				} else if ((elementsAndValues[0].getElement() == motionWE && elementsAndValues[0].getValue() == SwitchStatus.OFF)
						|| (elementsAndValues[1].getElement() == motionWE && elementsAndValues[1].getValue() == SwitchStatus.OFF)) {
					motionWE.setValue(SwitchStatus.OFF);
				}
			} else if (motionEE.getValue() == SwitchStatus.ON) {
				if ((elementsAndValues[0].getElement() == motionWE && elementsAndValues[0].getValue() == SwitchStatus.ON)
						|| (elementsAndValues[1].getElement() == motionWE && elementsAndValues[1].getValue() == SwitchStatus.ON)) {
					motionEE.setValue(SwitchStatus.OFF);
					motionWE.setValue(SwitchStatus.ON);
				} else if ((elementsAndValues[0].getElement() == motionEE && elementsAndValues[0].getValue() == SwitchStatus.OFF)
						|| (elementsAndValues[1].getElement() == motionEE && elementsAndValues[1].getValue() == SwitchStatus.OFF)) {
					motionEE.setValue(SwitchStatus.OFF);
				}
			} else {
				if (elementsAndValues[0].getValue() == SwitchStatus.ON) {
					elementsAndValues[0].getElement().setValue(SwitchStatus.ON);
				} else if (elementsAndValues[1].getValue() == SwitchStatus.ON) {
					elementsAndValues[1].getElement().setValue(SwitchStatus.ON);
				}
			}

			if (motionWE.getValue() == SwitchStatus.ON) {
				command.setSpeedRA(motionSpeed * (INVERT_RA ? -1 : 1));
				telescopeMotionWEP.setState(PropertyStates.OK);
			} else if (motionEE.getValue() == SwitchStatus.ON) {
				command.setSpeedRA(-motionSpeed * (INVERT_RA ? -1 : 1));
				telescopeMotionWEP.setState(PropertyStates.OK);
			} else {
				command.setSpeedRA(0);
				telescopeMotionWEP.setState(PropertyStates.IDLE);
			}
			sendCommand();

			try {
				updateProperty(telescopeMotionWEP);
			} catch (INDIException e) {
				e.printStackTrace();
			}
		}

		// if (property == enableAxisP) {
		// enableAxisP.setState(PropertyStates.IDLE);
		// for (int i = 0; i < elementsAndValues.length; i++) {
		// INDISwitchElement el = elementsAndValues[i].getElement();
		// SwitchStatus val = elementsAndValues[i].getValue();
		// if (val != el.getValue()) {
		// el.setValue(val);
		// enableAxisP.setState(PropertyStates.OK);
		// }
		// }
		// try {
		// updateProperty(enableAxisP);
		// } catch (INDIException e) {
		// e.printStackTrace();
		// }
		// }
		//
		if (property == abortMotionP) {
			if (elementsAndValues.length > 0) {
				if (elementsAndValues[0].getValue() == SwitchStatus.ON) {
					abortMotionP.setState(PropertyStates.OK);
					gotoActive = false;
					command.setSpeedDE(0);
					command.setSpeedRA(0);
					sendCommand();

					motionEE.setValue(SwitchStatus.OFF);
					motionWE.setValue(SwitchStatus.OFF);
					motionNE.setValue(SwitchStatus.OFF);
					motionSE.setValue(SwitchStatus.OFF);
				}
				try {
					updateProperty(abortMotionP);
					updateProperty(telescopeMotionNSP);
					updateProperty(telescopeMotionWEP);
				} catch (INDIException e) {
					e.printStackTrace();
				}
			}
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

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * laazotea.indi.driver.INDIConnectionHandler#driverConnect(java.util.Date)
	 */
	@Override
	public abstract void driverConnect(Date timestamp) throws INDIException;
	

	/**
	 * Called when the device is just connected
	 */
	protected void onConnected(){
		printMessage("Driver connected");
		addProperty(linkStatusP);
		addProperty(eqCoordP, "Driver connected");
		addProperty(sideP);
		addProperty(onCoordSetP);
		addProperty(telescopeMotionNSP);
		addProperty(telescopeMotionWEP);
		addProperty(abortMotionP);
		addProperty(motionRateP);

		syncCoordHA = getSiderealTime();
		syncStepHA = 0;
		syncCoordDE = 0;
		syncStepDE = 0;
	}
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * laazotea.indi.driver.INDIConnectionHandler#driverDisconnect(java.util
	 * .Date)
	 */
	@Override
	public abstract void driverDisconnect(Date timestamp) throws INDIException;
	
	/**
	 * Called when the device is just disconnected
	 */
	protected void onDisconnected(){
		printMessage("Driver disconnect");
		removeProperty(linkStatusP);
		removeProperty(eqCoordP);
		removeProperty(sideP);
		removeProperty(onCoordSetP);
		removeProperty(telescopeMotionNSP);
		removeProperty(telescopeMotionWEP);
		removeProperty(abortMotionP);
		removeProperty(motionRateP);
	}

	/**
	 * Computes the sidereal time
	 * 
	 * @return the sidereal time in hours
	 */
	public double getSiderealTime() {
		long now = (new Date()).getTime();
		double j2000 = 10957.5 * 3600 * 24 * 1e3;
		double D = (now - j2000) / 86400e3;
		double GMST = 18.697374558 + 24.06570982441908 * D;
		double lon = geographicCoordLongE.getValue();
		lon = lon / 360 * 24;
		double LST = GMST + lon;
		LST = LST % 24;
		return LST;
	}

	/**
	 * Send the current command message to the device
	 */
	protected abstract void sendCommand();

	
	/**
	 * update the position properties from the status message
	 */
	protected void updateStatus() {
		// System.out.print(lastStatusMesage);
		double HA = (lastStatusMessage.getHA() * (INVERT_RA ? -1 : 1) - syncStepHA) / STEP_BY_TURN * 24 + syncCoordHA;
		double RA = mod24(getSiderealTime() - HA);

		double DE = mod360((lastStatusMessage.getDE() * (INVERT_DE ? -1 : 1) - syncStepDE) / STEP_BY_TURN * 360
				* (sideEastE.getValue() == SwitchStatus.ON ? 1 : -1) + syncCoordDE);

		// if (DE > 90) {
		// DE = 180 - DE;
		// RA = mod24(RA + 12);
		// } else if (DE < -90) {
		// DE = -180 - DE;
		// RA = mod24(RA + 12);
		// }

		eqCoordRAE.setValue(RA);
		eqCoordDEE.setValue(DE);

		gotoUpdate();

		try {
			updateProperty(eqCoordP);
		} catch (INDIException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sync with the specified coordinates
	 * 
	 * @param RA
	 * @param DE
	 */
	private void syncCoordinates(double RA, double DE) {
		syncCoordDE = DE;
		syncStepDE = lastStatusMessage.getDE() * (INVERT_DE ? -1 : 1);
		syncCoordHA = getSiderealTime() - RA;
		syncStepHA = lastStatusMessage.getHA() * (INVERT_RA ? -1 : 1);
		eqCoordP.setState(PropertyStates.OK);
		updateStatus();
	}

	/**
	 * Go to the specified coordinates
	 * 
	 * @param RA
	 * @param DE
	 */
	private void gotoCoordinates(double RA, double DE) {
		gotoTargetRA = RA;
		gotoTargetDE = DE;
		gotoActive = true;
	}

	private void gotoUpdate() {
		if (gotoActive) {
			boolean regularTargetDE = (gotoTargetDE > -90) && (gotoTargetDE <= 90);
			boolean regularCurrentDE = (eqCoordDEE.getValue() > -90.) && (eqCoordDEE.getValue() <= 90.);
			float speedDE, speedRA;
			boolean commandChanged = false;

			// DE
			if ((regularTargetDE && !regularCurrentDE) || (!regularTargetDE && regularCurrentDE)) {
				// target and current position not in the same side. Go to north
				// pole before to continue
				speedDE = GOTO_SPEED * (regularCurrentDE ? 1 : -1);
			} else {
				double distanceDE = gotoTargetDE - eqCoordDEE.getValue();
				if (Math.abs(distanceDE) > GOTO_SLOW_DISTANCE) {
					speedDE = GOTO_SPEED * (distanceDE > 0 ? 1 : -1);
				} else if (Math.abs(distanceDE) > GOTO_STOP_DISTANCE) {
					speedDE = GOTO_SLOW_SPEED * (distanceDE > 0 ? 1 : -1);
				} else {
					speedDE = 0;
				}
			}

			// RA
			double distanceRA = mod24(gotoTargetRA - eqCoordRAE.getValue() + 12) - 12; // between
																						// -12
																						// and
																						// 12
			if (Math.abs(distanceRA) > GOTO_SLOW_DISTANCE * 24. / 360.) {
				speedRA = GOTO_SPEED * (distanceRA > 0 ? 1 : -1);
			} else if (Math.abs(distanceRA) > GOTO_STOP_DISTANCE * 24. / 360.) {
				speedRA = GOTO_SLOW_SPEED * (distanceRA > 0 ? 1 : -1);
			} else {
				speedRA = 0;
			}

			speedDE *= (INVERT_DE ? -1 : 1) * (sideEastE.getValue() == SwitchStatus.ON ? 1 : -1);
			speedRA *= (INVERT_RA ? -1 : 1);

			if (speedDE != command.getSpeedDE()) {
				command.setSpeedDE(speedDE);
				commandChanged = true;
			}

			if (speedRA != command.getSpeedRA()) {
				command.setSpeedRA(speedRA);
				commandChanged = true;
			}

			if (speedRA == 0 && speedDE == 0) {
				gotoActive = false;
			}

			if (commandChanged) {
				sendCommand();
			}

		}
	}

	/**
	 * returns the modulus 24 in [0, 24[
	 * 
	 * @param x
	 * @return x modulus 24 in [0, 24[
	 */
	private double mod24(double x) {
		double res = x % 24;
		if (res >= 0) {
			return res;
		} else {
			return res + 24;
		}
	}

	/**
	 * returns the modulus 360 in [-180, 180[
	 * 
	 * @param x
	 * @return x modulus 360 in [-180, 180[
	 */
	private double mod360(double x) {
		double res = x % 360;
		res += (res < 0 ? 360 : 0);
		res += (res >= 180 ? -360 : 0);
		return res;
	}
}