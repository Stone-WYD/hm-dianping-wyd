package com.hmdp.utils;

import org.springframework.format.Formatter;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MyDateFormatter implements Formatter<LocalDateTime> {

    private String pattern;

    public MyDateFormatter(MyDateAnno myDateAnno) {
        if (myDateAnno.value() != null) {
            pattern = myDateAnno.value();
        }else pattern = "yyyyMMdd HH:mm:ss";
    }

    @Override
    public LocalDateTime parse(String s, Locale locale) throws ParseException {
        return LocalDateTime.parse( s , DateTimeFormatter.ofPattern( pattern ));
    }

    @Override
    public String print(LocalDateTime localDateTime, Locale locale) {
         return localDateTime.format(DateTimeFormatter.ofPattern(pattern));
    }
}
