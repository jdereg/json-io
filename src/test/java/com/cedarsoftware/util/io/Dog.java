package com.cedarsoftware.util.io;

/**
 * @author John DeRegnaucourt
 */
public class Dog
{
    public int x;

    public class Leg
    {
        public int y;

        public int getParentX() { return x; }
    }

	public static class Shoe {
		private Shoe(Leg leg) {
			if (leg == null) {
				throw new IllegalArgumentException(
						"A Shoe without a leg ... what a pity");
			}
		}

		public static Shoe construct() {
			return new Shoe(new Dog().new Leg());
		}

	}
}
