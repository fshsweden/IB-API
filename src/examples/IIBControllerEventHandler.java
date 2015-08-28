package examples;

/* All events an app can get from TWS */
public interface IIBControllerEventHandler {
	void connectedToTws();
	void disconnectedFromTws();
	
	void infoFromTws (int id, int code /* 0 = OK, else ERROR */, String msg);
}
