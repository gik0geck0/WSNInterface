package edu.mines.wsninterface;

import java.util.List;

public interface PacketNotification {
	public void receivePacket(RxPacket packetData);
	public void receiveStringPacket(String packetData);
	public void sendStringPacket(String packetData);
	public void foundNodesList(List<NDResponse> nodes);
}
