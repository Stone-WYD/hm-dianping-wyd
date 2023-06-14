package com.hmdp.utils;

import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Parser;
import org.springframework.format.Printer;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class MyDateAnnoFormatterFactory implements AnnotationFormatterFactory<MyDateAnno> {

    @Override
    public Set<Class<?>> getFieldTypes() {
        Set<Class<?>> types = new HashSet<>();
        types.add(LocalDateTime.class);
        return types;
    }

    @Override
    public Printer<?> getPrinter(MyDateAnno myDateAnno, Class<?> aClass) {
        return new MyDateFormatter(myDateAnno);
    }

    @Override
    public Parser<?> getParser(MyDateAnno myDateAnno, Class<?> aClass) {
        return new MyDateFormatter(myDateAnno);
    }
}
