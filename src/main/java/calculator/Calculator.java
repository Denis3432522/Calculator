package calculator;

import libs.ConfigValidationException;
import libs.Prompter;
import libs.annots.*;

public class Calculator {

    public static void main(String[] args) {
        Prompter<User> prompter = new Prompter<>();

        try {
            prompter.configure(User.class);
            prompter.disableDefaultMessageTrailer();
        } catch (ConfigValidationException e) {
            System.out.println(e.getMessage());
        }

        User user = prompter.prompt();

        user.calculate();
    }
}

class User {

    final private int A = 10;
    final private double B = 6.25;
    final private int C = 5;

    @Prompt(msg= " Введите Ваш возраст:")
    @NotNegative(errMsg = "Возраст не может быть отрицательным")
    int age;

    @Prompt(msg = "Введите Ваш рост, в см. :")
    @NotNegative(errMsg = "Рост не может быть отрицательным!")
    int height;

    @Prompt(msg = "Введите Ваш вес:")
    @NotNegative(errMsg = "Вес не может быть отрицательным!")
    int weight;

    @Prompt(msg = """
                        Выберите степень Вашей физической активности от 1 до 5
                        1 - Малоподвижный образ жизни
                        2 - Низкая активность
                        3 - Умеренная активность
                        4 - Высокая активность
                        5 - Очень высокая активность
                        """)
    @IntOptionRange(errMsg = "Число должно быть от 1 до 5 !")
    @DoubleValues({1.2, 1.375, 1.550, 1.725, 1.900})
    double physicalActivity;

    @Prompt(msg = "Введите Ваш пол (М или Ж) :")
    @StringOptions(value = {"М", "Ж"}, errMsg = "Неверный ввод! Введите М или Ж!")
    @IntValues({5, -161})
    int genderCoefficient;

    public void calculate() {

        double result = (A * weight + B * height - C * age + genderCoefficient) * physicalActivity;
        System.out.print("Ваша суточная норма ккал. составляет: " + result);
    }
}