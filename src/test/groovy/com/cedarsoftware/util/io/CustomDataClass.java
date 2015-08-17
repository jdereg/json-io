package com.cedarsoftware.util.io;

class CustomDataClass
{
	//Ensure that custom reader is used
	public CustomDataClass(int i){
		if(i!=5){
			throw new IllegalArgumentException();
		}
	}

	private String test;

	public void setTest(String test) {
		this.test = test;
	}

	public String getTest() {
		return test;
	}
}
