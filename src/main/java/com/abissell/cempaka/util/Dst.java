/*
 * cempaka, an algorithmic trading platform written in Java
 * Copyright (C) 2023 Andrew Bissell
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.abissell.cempaka.util;

import java.util.EnumMap;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;

import com.abissell.logutil.LogDst;

public enum Dst implements LogDst {
    APP(null),
    STD_OUT("StdOut"),
    MKT_DATA("MktData"),
    EXEC("Exec");

    private final String name;
    Dst(String name) {
        this.name = name;
    }

    private static final EnumMap<Dst, Logger> loggers = configLoggers();

    @Override
    public Logger getLogger() {
        return loggers.get(this);
    }

    private static EnumMap<Dst, Logger> configLoggers() {
        var builder = ConfigurationBuilderFactory.newConfigurationBuilder();

        // Set to debug or trace if log4j2 is having problems
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("CempakaLogsBuilder");
        builder.addProperty("logdir", "log");

        // create a console appender and logger
        var appenderBuilder = builder.newAppender("Stdout", "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
        appenderBuilder.add(builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%msg%n%throwable"));
        builder.add(appenderBuilder);

        builder.add(builder.newLogger(STD_OUT.name, Level.INFO)
                .add(builder.newAppenderRef("Stdout"))
                .addAttribute("additivity", false));

        // create a standard rolling file triggering policy
        var triggeringPolicy = builder.newComponent("Policies")
                .addComponent(builder.newComponent("CronTriggeringPolicy")
                        .addAttribute("schedule", "0 0 0 * * ?"))
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy")
                        .addAttribute("size", "100M"));

        var rolloverStrategy = builder.newComponent("DefaultRolloverStrategy")
                .addAttribute("max", 20);

        // create a mkt data rolling file appender and logger
        var layoutBuilder = builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%msg%n");
        appenderBuilder = builder.newAppender("rollingMktData", "RollingFile")
                .addAttribute("fileName", "${logdir}/mktdata.log")
                .addAttribute("filePattern", "${logdir}/mktdata-%d{yyyyMMdd}-%i.log.gz")
                .add(layoutBuilder)
                .addComponent(triggeringPolicy)
                .addComponent(rolloverStrategy);
        builder.add(appenderBuilder);

        builder.add(builder.newLogger(MKT_DATA.name, Level.INFO)
                .add(builder.newAppenderRef("rollingMktData"))
                .addAttribute("additivity", false));

        // create an execution rolling file appender and logger
        appenderBuilder = builder.newAppender("rollingExec", "RollingFile")
                .addAttribute("fileName", "${logdir}/exec.log")
                .addAttribute("filePattern", "${logdir}/exec-%d{yyyyMMdd}-%i.log.gz")
                .add(layoutBuilder)
                .addComponent(triggeringPolicy)
                .addComponent(rolloverStrategy);
        builder.add(appenderBuilder);

        builder.add(builder.newLogger(EXEC.name, Level.INFO)
                .add(builder.newAppenderRef("rollingExec"))
                .addAttribute("additivity", false));

        // create the root appender
        layoutBuilder = builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid:%tn] %-5level: %msg%n");
        appenderBuilder = builder.newAppender("rolling", "RollingFile")
                .addAttribute("fileName", "${logdir}/app.log")
                .addAttribute("filePattern", "${logdir}/app-%d{yyyyMMdd}-%i.log.gz")
                .add(layoutBuilder)
                .addComponent(triggeringPolicy)
                .addComponent(rolloverStrategy);
        builder.add(appenderBuilder);

        // set loggers at WARN level for QFJ messages
        builder.add(builder.newLogger("quickfixj.msg.incoming", Level.WARN)
                .add(builder.newAppenderRef("rolling"))
                .addAttribute("additivity", false));
        builder.add(builder.newLogger("quickfixj.msg.outgoing", Level.WARN)
                .add(builder.newAppenderRef("rolling"))
                .addAttribute("additivity", false));

        // create the root logger
        builder.add(builder.newRootLogger(Level.INFO)
                .add(builder.newAppenderRef("rolling")));

        // initialize the config
        Configurator.initialize(builder.build());

        // populate and return the loggersMap
        var loggersMap = new EnumMap<Dst, Logger>(Dst.class);
        for (Dst dst : Dst.values()) {
            if (dst.name == null) {
                loggersMap.put(dst, LogManager.getLogger());
            } else {
                loggersMap.put(dst, LogManager.getLogger(dst.name));
            }
        }
        return loggersMap;
    }
}
