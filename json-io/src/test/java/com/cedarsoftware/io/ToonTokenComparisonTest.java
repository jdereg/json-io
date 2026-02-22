package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares BPE token counts between JSON and TOON output formats
 * using OpenAI's cl100k_base tokenizer (GPT-4 / ChatGPT) via jtokkit.
 *
 * This test provides quantitative evidence of TOON's token efficiency
 * for LLM consumption — a key selling point for the Baeldung article.
 */
class ToonTokenComparisonTest {

    private static Encoding encoding;

    @BeforeAll
    static void initTokenizer() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    // ==================== POJOs ====================

    static class Person {
        String name;
        int age;
        String email;

        Person() {}
        Person(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }
    }

    static class Employee {
        String name;
        int age;
        String department;
        double salary;

        Employee() {}
        Employee(String name, int age, String department, double salary) {
            this.name = name;
            this.age = age;
            this.department = department;
            this.salary = salary;
        }
    }

    static class Product {
        String sku;
        String name;
        double price;
        int quantity;
        String category;

        Product() {}
        Product(String sku, String name, double price, int quantity, String category) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.quantity = quantity;
            this.category = category;
        }
    }

    // ==================== Helper ====================

    private static int countTokens(String text) {
        return encoding.countTokens(text);
    }

    private static WriteOptions jsonOptions() {
        return new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();
    }

    private static void printComparison(String label, String json, String toon) {
        int jsonTokens = countTokens(json);
        int toonTokens = countTokens(toon);
        double savings = 100.0 * (jsonTokens - toonTokens) / jsonTokens;

        System.out.println("\n=== " + label + " ===");
        System.out.println("JSON (" + jsonTokens + " tokens):");
        System.out.println(json);
        System.out.println("\nTOON (" + toonTokens + " tokens):");
        System.out.println(toon);
        System.out.printf("\nToken savings: %d → %d (%.1f%% fewer tokens)%n",
                jsonTokens, toonTokens, savings);
    }

    // ==================== Tests ====================

    @Test
    void testSmallPersonList() {
        List<Person> people = Arrays.asList(
                new Person("Alice", 28, "alice@example.com"),
                new Person("Bob", 34, "bob@example.com"),
                new Person("Charlie", 22, "charlie@example.com")
        );

        String json = JsonIo.toJson(people, jsonOptions());
        String toon = JsonIo.toToon(people, null);

        printComparison("Small Person List (3 items)", json, toon);

        int jsonTokens = countTokens(json);
        int toonTokens = countTokens(toon);
        assertTrue(toonTokens < jsonTokens,
                "TOON should use fewer tokens than JSON: TOON=" + toonTokens + " JSON=" + jsonTokens);
    }

    @Test
    void testMediumEmployeeList() {
        List<Employee> employees = Arrays.asList(
                new Employee("Alice Johnson", 28, "Engineering", 95000),
                new Employee("Bob Smith", 34, "Marketing", 78000),
                new Employee("Charlie Brown", 22, "Engineering", 72000),
                new Employee("Diana Prince", 31, "Product", 105000),
                new Employee("Eve Williams", 29, "Engineering", 88000),
                new Employee("Frank Miller", 45, "Sales", 92000),
                new Employee("Grace Lee", 27, "Design", 81000),
                new Employee("Hank Patel", 38, "Engineering", 115000),
                new Employee("Iris Chen", 33, "Marketing", 76000),
                new Employee("Jack Davis", 41, "Product", 110000)
        );

        String json = JsonIo.toJson(employees, jsonOptions());
        String toon = JsonIo.toToon(employees, null);

        printComparison("Medium Employee List (10 items)", json, toon);

        int jsonTokens = countTokens(json);
        int toonTokens = countTokens(toon);
        double savings = 100.0 * (jsonTokens - toonTokens) / jsonTokens;

        assertTrue(toonTokens < jsonTokens,
                "TOON should use fewer tokens than JSON");
        assertTrue(savings > 20,
                "Expected > 20% token savings for uniform tabular data, got " + String.format("%.1f%%", savings));
    }

    @Test
    void testLargeProductCatalog() {
        List<Product> products = new ArrayList<>();
        String[] categories = {"Electronics", "Books", "Clothing", "Home", "Sports"};
        String[] prefixes = {"Widget", "Gadget", "Tool", "Device", "Accessory"};
        for (int i = 0; i < 25; i++) {
            products.add(new Product(
                    "SKU-" + String.format("%04d", i + 1),
                    prefixes[i % 5] + " " + (char)('A' + i % 26),
                    9.99 + (i * 5.50),
                    10 + i * 3,
                    categories[i % 5]
            ));
        }

        String json = JsonIo.toJson(products, jsonOptions());
        String toon = JsonIo.toToon(products, null);

        printComparison("Large Product Catalog (25 items)", json, toon);

        int jsonTokens = countTokens(json);
        int toonTokens = countTokens(toon);
        double savings = 100.0 * (jsonTokens - toonTokens) / jsonTokens;

        assertTrue(savings > 25,
                "Expected > 25% token savings for 25-item catalog, got " + String.format("%.1f%%", savings));
    }

    @Test
    void testNestedCompanyStructure() {
        Map<String, Object> company = new LinkedHashMap<>();
        company.put("name", "Acme Corp");
        company.put("founded", 2010);
        company.put("public", true);

        List<Map<String, Object>> departments = new ArrayList<>();
        String[] deptNames = {"Engineering", "Marketing", "Sales"};
        int[][] ages = {{28, 34, 22, 31}, {29, 45}, {27, 38, 33}};
        String[][] names = {
                {"Alice", "Bob", "Charlie", "Diana"},
                {"Eve", "Frank"},
                {"Grace", "Hank", "Iris"}
        };
        for (int d = 0; d < 3; d++) {
            Map<String, Object> dept = new LinkedHashMap<>();
            dept.put("name", deptNames[d]);
            List<Map<String, Object>> members = new ArrayList<>();
            for (int m = 0; m < names[d].length; m++) {
                Map<String, Object> member = new LinkedHashMap<>();
                member.put("name", names[d][m]);
                member.put("age", ages[d][m]);
                members.add(member);
            }
            dept.put("members", members);
            departments.add(dept);
        }
        company.put("departments", departments);

        String json = JsonIo.toJson(company, jsonOptions());
        String toon = JsonIo.toToon(company, null);

        printComparison("Nested Company Structure", json, toon);

        int jsonTokens = countTokens(json);
        int toonTokens = countTokens(toon);
        assertTrue(toonTokens < jsonTokens,
                "TOON should use fewer tokens even for nested structures");
    }

    @Test
    void testSingleObjectComparison() {
        Person person = new Person("Alice Johnson", 28, "alice.johnson@example.com");

        String json = JsonIo.toJson(person, jsonOptions());
        String toon = JsonIo.toToon(person, null);

        printComparison("Single Object", json, toon);

        int jsonTokens = countTokens(json);
        int toonTokens = countTokens(toon);
        // Single objects show modest savings (no tabular repetition benefit)
        assertTrue(toonTokens <= jsonTokens,
                "TOON should use same or fewer tokens for a single object");
    }

    @Test
    void testAggregateTokenSavings() {
        // Build a realistic mixed dataset
        List<Employee> employees = new ArrayList<>();
        String[] firstNames = {"Alice", "Bob", "Charlie", "Diana", "Eve", "Frank",
                "Grace", "Hank", "Iris", "Jack", "Kim", "Leo", "Mia", "Noah",
                "Olivia", "Pete", "Quinn", "Rosa", "Sam", "Tina"};
        String[] lastNames = {"Johnson", "Smith", "Brown", "Prince", "Williams",
                "Miller", "Lee", "Patel", "Chen", "Davis"};
        String[] depts = {"Engineering", "Marketing", "Sales", "Product", "Design"};
        for (int i = 0; i < 20; i++) {
            employees.add(new Employee(
                    firstNames[i] + " " + lastNames[i % 10],
                    25 + (i * 2) % 30,
                    depts[i % 5],
                    65000 + i * 3500
            ));
        }

        String json = JsonIo.toJson(employees, jsonOptions());
        String toon = JsonIo.toToon(employees, null);

        int jsonTokens = countTokens(json);
        int toonTokens = countTokens(toon);
        double savings = 100.0 * (jsonTokens - toonTokens) / jsonTokens;

        System.out.println("\n=== AGGREGATE COMPARISON (20 employees) ===");
        System.out.println("JSON tokens: " + jsonTokens);
        System.out.println("TOON tokens: " + toonTokens);
        System.out.printf("Token savings: %.1f%%%n", savings);
        System.out.println("JSON bytes: " + json.length());
        System.out.println("TOON bytes: " + toon.length());
        System.out.printf("Byte savings: %.1f%%%n",
                100.0 * (json.length() - toon.length()) / json.length());

        assertTrue(savings > 30,
                "Expected > 30% aggregate token savings for 20-item uniform list, got " + String.format("%.1f%%", savings));
    }
}
