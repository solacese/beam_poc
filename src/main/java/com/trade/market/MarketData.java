package com.trade.market;

public class MarketData {
	
	private String symbol;
	private String lastTraded;
	private String percentChange;
	public String getSymbol() {
		return symbol;
	}
	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}
	public String getLastTraded() {
		return lastTraded;
	}
	public void setLastTraded(String lastTraded) {
		this.lastTraded = lastTraded;
	}
	public String getPercentChange() {
		return percentChange;
	}
	public void setPercentChange(String percentChange) {
		this.percentChange = percentChange;
	}
	
	@Override
	public String toString(){
		return "\nSymbol="+getSymbol()+"::Last Traded"+getLastTraded()+"::Percent Change="+getPercentChange();
	}

}
