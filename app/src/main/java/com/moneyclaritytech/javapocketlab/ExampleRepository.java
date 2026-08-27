package com.moneyclaritytech.pocketforge;

import java.util.LinkedHashMap;
import java.util.Map;

/** Original clean-room examples written for PocketJava. */
public final class ExampleRepository {
    private ExampleRepository() {}

    public static Map<String, String> examples() {
        LinkedHashMap<String, String> e = new LinkedHashMap<>();
        e.put("Hello world", "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, Java!\");\n    }\n}\n");
        e.put("Simple calculator", "public class Main {\n    public static void main(String[] args) {\n        double a = 12;\n        double b = 4;\n        System.out.println(\"Add: \" + (a + b));\n        System.out.println(\"Multiply: \" + (a * b));\n    }\n}\n");
        e.put("Marks and grade", "public class Main {\n    public static void main(String[] args) {\n        int marks = 82;\n        if (marks >= 90) System.out.println(\"A\");\n        else if (marks >= 75) System.out.println(\"B\");\n        else if (marks >= 60) System.out.println(\"C\");\n        else System.out.println(\"Keep practising\");\n    }\n}\n");
        e.put("Multiplication table", "public class Main {\n    public static void main(String[] args) {\n        int n = 7;\n        for (int i = 1; i <= 10; i++) {\n            System.out.println(n + \" x \" + i + \" = \" + (n * i));\n        }\n    }\n}\n");
        e.put("Array average", "public class Main {\n    public static void main(String[] args) {\n        int[] values = {10, 20, 30, 40};\n        int total = 0;\n        for (int value : values) total += value;\n        System.out.println(\"Average = \" + (total / (double) values.length));\n    }\n}\n");
        e.put("Ask for a name", "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        System.out.print(\"Name: \" );\n        String name = sc.nextLine();\n        System.out.println(\"Welcome, \" + name + \"!\");\n    }\n}\n");
        e.put("Method practice", "public class Main {\n    static int square(int number) {\n        return number * number;\n    }\n\n    public static void main(String[] args) {\n        System.out.println(square(9));\n    }\n}\n");
        e.put("Class and object", "class Account {\n    String owner;\n    int balance;\n\n    Account(String owner, int balance) {\n        this.owner = owner;\n        this.balance = balance;\n    }\n}\n\npublic class Main {\n    public static void main(String[] args) {\n        Account a = new Account(\"Learner\", 500);\n        System.out.println(a.owner + \" has \" + a.balance);\n    }\n}\n");
        return e;
    }
}
