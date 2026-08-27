package com.moneyclaritytech.pocketforge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LessonRepository {
    private LessonRepository() {}

    public static List<Lesson> beginnerLessons() {
        List<Lesson> out = new ArrayList<>();
        out.add(new Lesson(
                "1. Hello, Java",
                "A Java program starts in main(). System.out.println prints one line to the console.",
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello, Java!\");\n" +
                "    }\n" +
                "}\n",
                "Hello, Java!",
                "Print a second line saying: I am coding on my phone."
        ));
        out.add(new Lesson(
                "2. Variables",
                "Variables are named boxes. String stores text; int stores whole numbers.",
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        String name = \"Java Learner\";\n" +
                "        int lessons = 2;\n" +
                "        System.out.println(name);\n" +
                "        System.out.println(lessons);\n" +
                "    }\n" +
                "}\n",
                "Java Learner\n2",
                "Add a city variable and print it."
        ));
        out.add(new Lesson(
                "3. Calculations",
                "Java can calculate with +, -, *, / and %. Put numbers in variables and combine them.",
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int a = 12;\n" +
                "        int b = 5;\n" +
                "        System.out.println(a + b);\n" +
                "        System.out.println(a * b);\n" +
                "    }\n" +
                "}\n",
                "17\n60",
                "Print the remainder when 12 is divided by 5. Hint: %."
        ));
        out.add(new Lesson(
                "4. if / else",
                "An if statement lets a program make a decision based on true or false.",
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int age = 20;\n" +
                "        if (age >= 18) {\n" +
                "            System.out.println(\"Adult\");\n" +
                "        } else {\n" +
                "            System.out.println(\"Minor\");\n" +
                "        }\n" +
                "    }\n" +
                "}\n",
                "Adult",
                "Change age to 15 and predict the result before running it."
        ));
        out.add(new Lesson(
                "5. for loop",
                "A loop repeats instructions. This one prints numbers 1 through 5.",
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        for (int i = 1; i <= 5; i++) {\n" +
                "            System.out.println(i);\n" +
                "        }\n" +
                "    }\n" +
                "}\n",
                "1\n2\n3\n4\n5",
                "Make the loop print 1 through 10."
        ));
        out.add(new Lesson(
                "6. while loop",
                "A while loop repeats while its condition remains true.",
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int n = 3;\n" +
                "        while (n > 0) {\n" +
                "            System.out.println(n);\n" +
                "            n--;\n" +
                "        }\n" +
                "        System.out.println(\"Go!\");\n" +
                "    }\n" +
                "}\n",
                "3\n2\n1\nGo!",
                "Start the countdown at 5."
        ));
        out.add(new Lesson(
                "7. Methods",
                "Methods group reusable instructions. You can call the same method many times.",
                "public class Main {\n" +
                "    static void greet(String name) {\n" +
                "        System.out.println(\"Hello, \" + name);\n" +
                "    }\n\n" +
                "    public static void main(String[] args) {\n" +
                "        greet(\"Aman\");\n" +
                "        greet(\"Riya\");\n" +
                "    }\n" +
                "}\n",
                "Hello, Aman\nHello, Riya",
                "Call greet() once more with another name."
        ));
        out.add(new Lesson(
                "8. Arrays",
                "An array stores several values of the same type under one variable.",
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int[] marks = {72, 81, 90};\n" +
                "        System.out.println(marks[0]);\n" +
                "        System.out.println(marks.length);\n" +
                "    }\n" +
                "}\n",
                "72\n3",
                "Print the third mark. Remember array positions start at 0."
        ));
        out.add(new Lesson(
                "9. User input",
                "Scanner reads text or numbers typed into standard input.",
                "import java.util.Scanner;\n\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        System.out.print(\"Your name: \" );\n" +
                "        String name = sc.nextLine();\n" +
                "        System.out.println(\"Welcome, \" + name);\n" +
                "    }\n" +
                "}\n",
                "Your name: <what you typed>\nWelcome, <what you typed>",
                "Ask for a city and print: City = <city>."
        ));
        out.add(new Lesson(
                "10. Classes & objects",
                "A class is a blueprint. An object is one thing created from that blueprint.",
                "class Person {\n" +
                "    String name;\n" +
                "    Person(String name) { this.name = name; }\n" +
                "    void introduce() { System.out.println(\"I am \" + name); }\n" +
                "}\n\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Person p = new Person(\"Java Learner\");\n" +
                "        p.introduce();\n" +
                "    }\n" +
                "}\n",
                "I am Java Learner",
                "Create a second Person object and call introduce()."
        ));
        return Collections.unmodifiableList(out);
    }
}
