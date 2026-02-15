package com.cedarsoftware.io.models;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reusable university graph fixture for read-path matrix tests.
 */
public final class UniversityFixture {
    private UniversityFixture() {
    }

    public static University createSampleUniversity() {
        University u = new University();
        u.name = "North Ridge University";
        u.departmentsByCode = new LinkedHashMap<>();
        u.honorsStudents = new ArrayList<>();
        u.advisorAssignments = new LinkedHashMap<>();
        u.oddMapKeys = new LinkedHashMap<>();

        Department cs = new Department();
        cs.code = "CS";
        cs.name = "Computer Science";
        cs.tags = new LinkedHashSet<>();
        cs.tags.add("engineering");
        cs.tags.add("stem");
        cs.courses = new ArrayList<>();

        Course algo = new Course();
        algo.courseCode = "CS-501";
        algo.credits = 4;
        algo.externalId = UUID.fromString("4f7f2c64-99b5-4f71-a0c1-cfa41e6c3561");
        algo.kickoff = ZonedDateTime.of(2025, 9, 1, 9, 0, 0, 0, ZoneOffset.UTC);
        algo.meetingDays = BitSet.valueOf(new long[]{0b10101L});

        Course systems = new Course();
        systems.courseCode = "CS-530";
        systems.credits = 3;
        systems.externalId = UUID.fromString("4a050f5b-586a-4d49-ac44-a4eef2234982");
        systems.kickoff = ZonedDateTime.of(2025, 9, 2, 14, 30, 0, 0, ZoneOffset.UTC);
        systems.meetingDays = BitSet.valueOf(new long[]{0b01010L});

        cs.courses.add(algo);
        cs.courses.add(systems);
        u.departmentsByCode.put(cs.code, cs);

        Student s1 = new Student();
        s1.id = "s-100";
        s1.name = "Alice";
        s1.age = 20;
        s1.gpa = 3.9;
        s1.scores = new ArrayList<>();
        s1.scores.add(95);
        s1.scores.add(92);

        Student s2 = new Student();
        s2.id = "s-200";
        s2.name = "Bob";
        s2.age = 21;
        s2.gpa = 3.7;
        s2.scores = new ArrayList<>();
        s2.scores.add(89);
        s2.scores.add(91);

        u.honorsStudents.add(s1);
        u.honorsStudents.add(s2);

        PersonKey k1 = new PersonKey();
        k1.id = "f-1";
        k1.name = "Prof Ada";
        PersonKey k2 = new PersonKey();
        k2.id = "f-2";
        k2.name = "Prof Turing";
        u.advisorAssignments.put(k1, s1);
        u.advisorAssignments.put(k2, s2);

        Map<String, Object> nestedKey = new LinkedHashMap<>();
        nestedKey.put("bucket", "alpha");
        nestedKey.put("rank", 1);
        u.oddMapKeys.put(nestedKey, "present");

        List<Object> listInsideArray = new ArrayList<>();
        listInsideArray.add("note");
        listInsideArray.add(42);
        listInsideArray.add(null);

        Object[] objectArray = new Object[]{"x", 7L, true};
        int[] primitiveArray = new int[]{1, 2, 3};
        long[] longArray = new long[]{4L, 5L};

        Map<String, Object> mapInsideArray = new LinkedHashMap<>();
        mapInsideArray.put("inner", "value");
        mapInsideArray.put("n", 9);

        u.mixedArtifacts = new Object[]{
                listInsideArray,
                objectArray,
                primitiveArray,
                longArray,
                mapInsideArray
        };

        return u;
    }

    public static class University {
        public String name;
        public Map<String, Department> departmentsByCode;
        public List<Student> honorsStudents;
        public Object[] mixedArtifacts;
        public Map<PersonKey, Student> advisorAssignments;
        public Map<Object, String> oddMapKeys;
    }

    public static class Department {
        public String code;
        public String name;
        public Set<String> tags;
        public List<Course> courses;
    }

    public static class Course {
        public String courseCode;
        public int credits;
        public UUID externalId;
        public ZonedDateTime kickoff;
        public BitSet meetingDays;
    }

    public static class Student {
        public String id;
        public String name;
        public int age;
        public double gpa;
        public List<Integer> scores;
    }

    public static class PersonKey {
        public String id;
        public String name;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PersonKey)) {
                return false;
            }
            PersonKey personKey = (PersonKey) o;
            return Objects.equals(id, personKey.id) && Objects.equals(name, personKey.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }
}
