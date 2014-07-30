package farom.astroiddriver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CmdMessage {
	public static final int MESSAGE_SIZE = 9;
	protected float speedRA;
	protected float speedDE;
	
	public CmdMessage(float moveSpeedRA,float moveSpeedDE) {
		speedRA=moveSpeedRA;
		speedDE=moveSpeedDE;
	}
	
	public CmdMessage() {
		speedRA=0;
		speedDE=0;
	}
	
	public byte[] getBytes(){
		ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_SIZE);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putFloat(0, speedRA);
		buffer.putFloat(4, speedDE);
		
		byte[] array = buffer.array();
		int sum = 0;
		for(int i=0; i<MESSAGE_SIZE-1; i++){
			sum+=array[i];
		}
		array[MESSAGE_SIZE-1]=(byte) (sum & 0xFF);
		
//		System.out.print("send: ");
//		for(int i =0; i<MESSAGE_SIZE; i++){
//			System.out.printf("%02X ", array[i]);
//		}
//		System.out.println("");
		return array;
	}

	/**
	 * @return the speedRA
	 */
	public float getSpeedRA() {
		return speedRA;
	}

	/**
	 * @param speedRA the speedRA to set
	 */
	public void setSpeedRA(float speedRA) {
		this.speedRA = speedRA;
	}

	/**
	 * @return the speedDE
	 */
	public float getSpeedDE() {
		return speedDE;
	}

	/**
	 * @param speedDE the speedDE to set
	 */
	public void setSpeedDE(float speedDE) {
		this.speedDE = speedDE;
	}

}
