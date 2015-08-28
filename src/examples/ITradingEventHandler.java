package examples;

public interface ITradingEventHandler {
	public void orderAdded(IBOrder order);
	public void orderModifed(IBOrder order);
	public void orderCancelled(IBOrder order);
	
	public void tradeAdded(IBOrder order, IBExecution execution);
	
}
