package com.cedarsoftware.util.io;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class Dog
{
    public int x;

    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        return result;
    }

    public boolean equals(Object obj)
    {
        if (this.is(obj))
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        Dog other = (Dog) obj;
        if (x != other.x)
        {
            return false;
        }
        return true;
    }

    public static createLeg(Dog dog)
    {
        return new Leg(dog)
    }

    public class Leg
    {
        public int y;

        public int getParentX()
        {
            return x;
        }

        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode()
            result = prime * result + y;
            return result;
        }

        public boolean equals(Object obj)
        {
            if (this.is(obj))
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            Leg other = (Leg) obj;
            if (!getOuterType().equals(other.getOuterType()))
            {
                return false;
            }
            if (y != other.y)
            {
                return false;
            }
            return true;
        }

        private Dog getOuterType()
        {
            return Dog.this;
        }
    }

    public static class Shoe
    {
        private Shoe(Leg leg)
        {
            if (leg == null)
            {
                throw new IllegalArgumentException(
                        "A Shoe without a leg ... what a pity")
            }
        }

        public static Shoe construct()
        {
            return new Shoe(new Leg(new Dog()))
        }
    }

    public static class OtherShoe
    {
        private Leg leg;

        private OtherShoe(Leg leg)
        {
            if (leg == null)
            {
                throw new IllegalArgumentException("A Shoe without a leg ... what a pity")
            }
            this.leg = leg;
        }

        public static OtherShoe construct()
        {
            Leg leg2 = new Leg(new Dog())
            leg2.y = 5;
            return new OtherShoe(leg2)
        }

        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((leg == null) ? 0 : leg.hashCode())
            return result;
        }

        public boolean equals(Object obj)
        {
            if (this.is(obj))
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            OtherShoe other = (OtherShoe) obj;
            if (leg == null)
            {
                if (other.leg != null)
                {
                    return false;
                }
            }
            else if (!leg.equals(other.leg))
            {
                return false;
            }
            return true;
        }

    }
}
