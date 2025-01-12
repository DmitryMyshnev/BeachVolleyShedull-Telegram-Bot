package com.enterprise.myshnev.telegrambot.scheduler.commands;


import com.enterprise.myshnev.telegrambot.scheduler.bot.TelegramBot;
import com.enterprise.myshnev.telegrambot.scheduler.model.Workout;
import com.enterprise.myshnev.telegrambot.scheduler.servises.messages.SendMessageService;
import com.enterprise.myshnev.telegrambot.scheduler.servises.user.UserService;
import com.enterprise.myshnev.telegrambot.scheduler.servises.workout.WorkoutService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.ResourceUtils;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.enterprise.myshnev.telegrambot.scheduler.commands.CommandUtils.*;


public class AddWorkoutCommand implements Command {
    private final SendMessageService sendMessageService;
    private final WorkoutService workoutService;
    private final UserService userService;
    private boolean accept = false;
    private final String SUPER_ADMIN;
    private final Integer DEFAULT_MAX_COUNTER;
    public static Logger LOGGER = LogManager.getLogger(AddWorkoutCommand.class);

    public AddWorkoutCommand(SendMessageService sendMessageService, UserService userService, WorkoutService workoutService) {
        this.sendMessageService = sendMessageService;
        this.workoutService = workoutService;
        this.userService = userService;
        SUPER_ADMIN = SuperAdminUtils.getInstance().getIdSuperAdminFromFileConfig();
        DEFAULT_MAX_COUNTER = SuperAdminUtils.getInstance().getDEFAULT_MAX_COUNTER();
    }

    @Override
    public void execute(Update update) {
        String chatId = getChatId(update);
        String command;
        String message;
        TelegramBot tb = TelegramBot.getInstance();
        userService.findByChatId(chatId).stream().filter(f -> (f.isEqualsRole("COACH")))
                .findFirst()
                .ifPresentOrElse(user -> accept = true,
                        () -> {
                            if (chatId.equals(SUPER_ADMIN)) {
                                accept = true;
                            } else {
                                sendMessageService.deleteMessage(chatId, getMessageId(update));
                                accept = false;
                            }
                        });
        if (accept) {
            accept = false;
            if (getCallbackQuery(update) != null) {
                command = Objects.requireNonNull(getCallbackQuery(update)).split("/")[0];

                if (command.equals("add_workout")) {
                    message = "Введите ключевое слово <strong>add/</strong> и добавьте день недели, время и максимальное количество участников " +
                            "через запятую.\nНапример: <strong>add/</strong><i>пн, 11:00, 4</i>\n" +
                            "(можно не указывать кол-во участников, по умолчанию - 8)";
                    sendMessageService.sendMessage(chatId, message, null);
                }
            } else {
                String text = getText(update).toLowerCase();
                Pattern pattern = Pattern.compile("(add/)(\\s*)(пн|вт|ср|чт|пт|сб|вс)(\\s*)");
                Matcher matcher = pattern.matcher(text);
                if (!matcher.find()) {
                    sendMessageService.sendMessage(chatId, "Отсутствует или неверно указан день недели", null);
                } else {
                    pattern = Pattern.compile("(add/)(\\s*)(пн|вт|ср|чт|пт|сб|вс)(\\s*)(,)(\\s*)");
                    matcher = pattern.matcher(text);
                    if (!matcher.find()) {
                        sendMessageService.sendMessage(chatId, "Отсутствует запятая или указан другой символ", null);
                    } else {
                        pattern = Pattern.compile("(add/)(\\s*)(пн|вт|ср|чт|пт|сб|вс)(\\s*)(,)(\\s*)(2[0-3]|1\\d|\\d)(:)([0-5][0-9])(\\s*),*(\\d*)$");
                        matcher = pattern.matcher(text);
                        if (!matcher.find()) {
                            sendMessageService.sendMessage(chatId, "Отсутствует или неверно указано время. Укажите время в формате <i>часы:минуты</i>", null);
                        } else {
                            String weekOfDay = Objects.requireNonNull(text).trim().split("/")[1].trim().split(",")[0].trim();
                            String time = Objects.requireNonNull(text).split("/")[1].trim().split(",")[1].trim();
                            String maxCount = null;
                            if (text.split("/")[1].split(",").length == 3) {
                                maxCount = Objects.requireNonNull(text).split("/")[1].split(",")[2].trim();
                            }
                            AtomicBoolean isExist = new AtomicBoolean(false);
                            workoutService.findAllWorkout()
                                    .forEach(w -> isExist.set(w.getDayOfWeek().equals(weekOfDay) && w.getTime().equals(time)));
                            if (!isExist.get()) {
                                Workout workout = new Workout(chatId, weekOfDay, time);
                                if (maxCount != null) {
                                    workout.setMaxCountUser(Integer.parseInt(maxCount));
                                } else {
                                    workout.setMaxCountUser(DEFAULT_MAX_COUNTER);
                                }
                                workoutService.saveWorkout(workout);
                                sendMessageService.sendMessage(chatId, "✅ Тренировка добавлена!", null);
                            } else {
                                sendMessageService.sendMessage(chatId, "❗ Такая тренировка уже существует.", null);
                            }
                        }

                    }
                }
            }
        }
        if (!tb.notifyMessageId.isEmpty()) {
            sendMessageService.deleteMessage(getChatId(update), Objects.requireNonNull(TelegramBot.getInstance().notifyMessageId.poll()).getMessageId());
        }
       // tb.filterQuery.set(null);
    }



}
