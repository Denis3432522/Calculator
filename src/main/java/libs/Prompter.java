package libs;

import libs.annots.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Prompter<T> {

    private Class<T> entity;
    private String msgTrailer = " Please try again.";
    private static Scanner scanner = new Scanner(System.in);

    public void disableDefaultMessageTrailer() {
        msgTrailer = "";
    }

    public void setMessageTrailer(String str) {
        msgTrailer = str;
    }

    public void configure(Class<T> clazz) throws ConfigValidationException {
        Validator.validateFields(clazz);
        entity = clazz;
    }

    public T prompt() {
        if(entity == null)
            throw new NullPointerException("Entity is not specified");

        T o;
        try {
            Constructor<T> constructor = entity.getDeclaredConstructor();
            constructor.setAccessible(true);
            o = constructor.newInstance();

        } catch (Exception e) {
            System.out.println("You must leave a class with no-arguments constructor");
            return null;
        }

        for(Field field : entity.getDeclaredFields()) {
            if(!field.isAnnotationPresent(Prompt.class))
                continue;
            promptField(field, o);
        }

        return o;
    }

    private void promptField(Field field, T o) {
        String msg = retrievePromptMsg(field);
        String typeName = field.getType().getName();

        while (true) {
            try {
                String input = input(msg);
                switch (typeName) {
                    case "java.lang.String" -> handleFieldAsString(field, input, o);
                    case "double" -> handleFieldAsDouble(field, input, o);
                    case "int" -> handleFieldAsInteger(field, input, o);
                    default -> throw new RuntimeException("Internal error occurred");
                }
                break;
            } catch (InvalidInputException e) {
                System.out.println(e.getMessage() + msgTrailer);
            }
        }
    }

    private void handleFieldAsString(Field field, String input, Object o) throws InvalidInputException {

        if(field.isAnnotationPresent(StringValues.class)) {
            if(hasOptions(field)) {
                int index = getIndexByOption(field, input);
                setField(o, field, getStringValue(field, index));
            } else {
                checkStringValue(field, input);
                setField(o, field, input);
            }

        } else {
            setField(o, field, input);
        }
    }

    private void handleFieldAsDouble(Field field, String input, T o) throws InvalidInputException {
        if(hasOptions(field)) {
            int index = getIndexByOption(field, input);
            setField(o, field, getDoubleValue(field, index));
            return;
        }

        double num;
        try {
            num = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("You need to enter a floating-point number");
        }

        if(field.isAnnotationPresent(DoubleValues.class)) {
            checkDoubleValue(field, num);
            setField(o, field, num);
        }
        else if (field.isAnnotationPresent(NotNegative.class)) {
            checkNotNegative(field, num);
            setField(o, field, num);
        }
        else {
            setField(o, field, num);
        }

    }

    private void handleFieldAsInteger(Field field, String input, T o) throws InvalidInputException {
        if(hasOptions(field)) {
            int index = getIndexByOption(field, input);
            setField(o, field, getIntValue(field, index));
            return;
        }

        int num;
        try {
            num = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("You need to enter a integer number");
        }

        if(field.isAnnotationPresent(IntValues.class)) {
                checkIntValue(field, num);
                setField(o, field, num);
        }
        else if (field.isAnnotationPresent(NotNegative.class)) {
            checkNotNegative(field, num);
            setField(o, field, num);
        }
        else {
            setField(o, field, num);
        }
    }

    private boolean hasOptions(Field field) {
        return field.isAnnotationPresent(IntOptionRange.class) || field.isAnnotationPresent(IntOptions.class)
                || field.isAnnotationPresent(StringOptions.class);
    }

    private void setField(Object o, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(o, value);
        } catch (Exception e) {
            System.out.println("Internal error occurred.");
            System.out.println("   " + e.getMessage());
        }
    }

    private int getIndexByOption(Field field, String input) throws InvalidInputException {

        if(field.isAnnotationPresent(StringOptions.class)) {
            StringOptions sO = field.getDeclaredAnnotation(StringOptions.class);
            return checkStringOptions(sO, input);
        }
        else if(field.isAnnotationPresent(IntOptions.class)) {
            IntOptions iO = field.getDeclaredAnnotation(IntOptions.class);
            return checkIntOptions(iO, input);
        }
        else if(field.isAnnotationPresent(IntOptionRange.class)) {
            IntOptionRange iR = field.getDeclaredAnnotation(IntOptionRange.class);
            return checkIntOptionRange(iR, input);
        }

        throw new RuntimeException("Internal error occurred");
    }

    private int checkStringOptions(StringOptions sO, String input) throws InvalidInputException {
        String[] options = sO.value();
        int ind = List.of(options).indexOf(input);

        if(ind == -1)
            throw new InvalidInputException(sO.errMsg());
        return ind;
    }

    private int checkIntOptions(IntOptions iO, String input) throws InvalidInputException {
        int num;
        try {
            num = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new InvalidInputException(iO.errMsg());
        }

        int[] options = iO.value();
        for(int i=0; i<options.length; i++) {
            if(options[i] == num)
                return i;
        }
        throw new InvalidInputException(iO.errMsg());
    }

    private int checkIntOptionRange(IntOptionRange iR, String input) throws InvalidInputException {
        int num;

        try {
            num = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new InvalidInputException(iR.errMsg());
        }

        if(iR.from() > num || iR.to() < num)
            throw new InvalidInputException(iR.errMsg());
        return num - iR.from();
    }

    private String getStringValue(Field field, int index) {
        String[] values = field.getDeclaredAnnotation(StringValues.class).value();
        return values[index];
    }

    private int getIntValue(Field field, int index) {
        int[] values = field.getDeclaredAnnotation(IntValues.class).value();
        return values[index];
    }

    private double getDoubleValue(Field field, int index) {
        double[] values = field.getDeclaredAnnotation(DoubleValues.class).value();
        return values[index];
    }

    private void checkStringValue(Field field, String input) throws InvalidInputException {
        StringValues sV = field.getDeclaredAnnotation(StringValues.class);
        String[] values = sV.value();
        if(Arrays.stream(values).noneMatch(s -> s.equals(input)))
            throw new InvalidInputException(sV.errMsg());
    }

    private void checkIntValue(Field field, int input) throws InvalidInputException {
        IntValues iV = field.getDeclaredAnnotation(IntValues.class);
        int[] values = iV.value();
        if(Arrays.stream(values).noneMatch(i -> i == input))
            throw new InvalidInputException(iV.errMsg());
    }

    private void checkDoubleValue(Field field, double input) throws InvalidInputException {
        DoubleValues dV = field.getDeclaredAnnotation(DoubleValues.class);
        double[] values = dV.value();
        if(Arrays.stream(values).noneMatch(d -> d == input))
            throw new InvalidInputException(dV.errMsg());
    }

    private void checkNotNegative(Field field, Number num) throws InvalidInputException {
        if(num.doubleValue() < 0)
            throw new InvalidInputException(field.getDeclaredAnnotation(NotNegative.class).errMsg());
    }

    private String retrievePromptMsg(Field field) {
        String msg = field.getDeclaredAnnotation(Prompt.class).msg();

        if(msg.equals("")) {
            String name = field.getName();
            msg = "Enter your " + name;
        }
        return msg;
    }

    private static String input(String msg) {
        System.out.println(msg);
        return scanner.nextLine();
    }

    private static class Validator {

        private static final Set<String> onlyStringAnnotations = Set.of("Prompt", "IntOptionRange", "StringOptions", "IntOptions" ,"StringValues");
        private static final Set<String> onlyDoubleAnnotations = Set.of("Prompt", "NotNegative", "IntOptionRange", "StringOptions", "IntOptions", "DoubleValues");
        private static final Set<String> onlyIntegerAnnotations = Set.of("Prompt", "NotNegative", "IntOptionRange", "StringOptions", "IntOptions", "IntValues");

        private static void  validateFields(Class<?> entity) throws ConfigValidationException {
            for(Field field : entity.getDeclaredFields()) {
                if(!field.isAnnotationPresent(Prompt.class))
                    continue;

                String typeName = field.getType().getName();
                switch (typeName) {
                    case "java.lang.String" -> validateFieldAsString(field);
                    case "double" -> validateFieldAsDouble(field);
                    case "int" -> validateFieldAsInteger(field);
                    default -> throw new ConfigValidationException("Only string, double and integer prompt types are allowed");
                }
            }
        }

        private static void validateFieldAsString(Field field) throws ConfigValidationException {
            hasAllowedAnnotations(field, onlyStringAnnotations);
            StringValues values = field.getAnnotation(StringValues.class);
            int valuesLen = values == null ? -1 : values.value().length;
            validateOptionsAndValues(field,  valuesLen);
            validateIntRange(field, valuesLen);
        }

        private static void validateFieldAsDouble(Field field) throws ConfigValidationException {
            hasAllowedAnnotations(field, onlyDoubleAnnotations);
            DoubleValues values = field.getAnnotation(DoubleValues.class);
            int valuesLen = values == null ? -1 : values.value().length;
            validateOptionsAndValues(field,  valuesLen);
            validateIntRange(field, valuesLen);
            validateNotNegative(field);
        }

        private static void validateFieldAsInteger(Field field) throws ConfigValidationException {
            hasAllowedAnnotations(field, onlyIntegerAnnotations);
            IntValues values = field.getAnnotation(IntValues.class);
            int valuesLen = values == null ? -1 : values.value().length;
            validateOptionsAndValues(field,  valuesLen);
            validateIntRange(field, valuesLen);
            validateNotNegative(field);

        }

        private static void validateOptionsAndValues(Field field, int numberOfValues) throws ConfigValidationException {
            boolean sO = field.isAnnotationPresent(StringOptions.class);
            boolean iO = field.isAnnotationPresent(IntOptions.class);

            if(sO && iO)
                throw new ConfigValidationException("You can use only one type of Options");
            if(sO || iO) {
                if(numberOfValues == -1)
                    throw new ConfigValidationException("If Options provided you must set Values");
                if(iO) {
                    int iOLen = field.getDeclaredAnnotation(IntOptions.class).value().length;
                    if(numberOfValues != iOLen)
                        throw new ConfigValidationException("The number of options must equal to the number of values");
                }

                if(sO) {
                    int sOLen = field.getDeclaredAnnotation(StringOptions.class).value().length;
                    if(numberOfValues != sOLen)
                        throw new ConfigValidationException("The number of options must equal to the number of values");
                }
            }
        }

        private static void validateIntRange(Field field, int numberOfValues) throws ConfigValidationException {
            if(!field.isAnnotationPresent(IntOptionRange.class))
                return;

            int from = field.getDeclaredAnnotation(IntOptionRange.class).from();
            int to = field.getDeclaredAnnotation(IntOptionRange.class).to();
            int margin = to - from + 1;

            if(margin < 2)
                throw new ConfigValidationException("The range is not valid.");

            if(field.isAnnotationPresent(StringOptions.class) || field.isAnnotationPresent(IntOptions.class))
                throw new ConfigValidationException("You cannot use Range and Options together");

            if(numberOfValues == -1)
                throw new ConfigValidationException("If Range provided you must set Values");

            if(margin != numberOfValues)
                throw new ConfigValidationException("The margin between From and To must equal to the number of values");
        }

        private static void validateNotNegative(Field field) throws ConfigValidationException {
            if(!field.isAnnotationPresent(NotNegative.class))
                return;

            if(field.getType().getName().equals("java.lang.String"))
                throw new ConfigValidationException("You cannot apply NotNegative to String");

            if(field.isAnnotationPresent(StringValues.class) || field.isAnnotationPresent(IntValues.class)
                    || field.isAnnotationPresent(DoubleValues.class))
                throw new ConfigValidationException("You cannot use NotNegative and Values together");
        }

        private static void hasAllowedAnnotations(Field field, Set<String> allowedAnnotations) throws ConfigValidationException {
            Annotation[] annotations = field.getDeclaredAnnotations();
            for(Annotation annotation : annotations) {
                String fullName = annotation.annotationType().getName();
                if(!allowedAnnotations.contains(getOnlyName(fullName)))
                    throw new ConfigValidationException(getOnlyName(fullName) + " annotation cannot be applied to " + getTypeName(field) + " field");
            }
        }

        private static String getTypeName(Field field) {
            String typeName = field.getType().getName();
            return switch (typeName) {
                        case "java.lang.String" -> "string";
                        case "int" -> "integer";
                        default -> "double";
            };
        }

        private static String getOnlyName(String str) {
            String[] parts = str.split("\\.");
            return parts[parts.length-1];
        }
    }
}
