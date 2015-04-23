package com.cedarsoftware.util.io

class WithoutCustomReaderClass {
	private String test;
	private CustomReaderClass customReaderInner;
	
	public void setTest(String test) {
		this.test = test;
	}
	
	public String getTest() {
		return test;
	}
	
	public void setCustomReaderInner(CustomReaderClass customReaderInner) {
		this.customReaderInner = customReaderInner;
	}
	
	public CustomReaderClass getCustomReaderInner() {
		return customReaderInner;
	}
}
