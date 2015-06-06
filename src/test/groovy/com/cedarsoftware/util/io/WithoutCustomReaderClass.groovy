package com.cedarsoftware.util.io

class WithoutCustomReaderClass {
	private String test;
	private CustomDataClass customReaderInner;

	public void setTest(String test) {
		this.test = test;
	}

	public String getTest() {
		return test;
	}

	public void setCustomReaderInner(CustomDataClass customReaderInner) {
		this.customReaderInner = customReaderInner;
	}

	public CustomDataClass getCustomReaderInner() {
		return customReaderInner;
	}
}
