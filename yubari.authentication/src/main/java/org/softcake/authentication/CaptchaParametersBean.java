/*
 * Copyright 2018 softcake.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softcake.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IllegalFormatException;

public class CaptchaParametersBean {
    private static Logger LOGGER = LoggerFactory.getLogger(CaptchaParametersBean.class);
    private static final String
        URL_PARAMETERS_PATTERN
        = "?width=%d&pin_l_bg=%s&pin_r_bg=%s&pin_fg=%s&needBtns=%b&boxes_bg=%s&boxes_fg=%s&trans=%b&captcha_bg=%s";
    private int width;
    private String pin_l_bg;
    private String pin_r_bg;
    private String pin_fg;
    private boolean needBtns;
    private String boxes_bg;
    private String boxes_fg;
    private boolean trans;
    private String captcha_bg;

    public CaptchaParametersBean(int width,
                                 String pin_l_bg,
                                 String pin_r_bg,
                                 String pin_fg,
                                 boolean needBtns,
                                 String boxes_bg,
                                 String boxes_fg,
                                 boolean trans,
                                 String captcha_bg) {

        this.width = width;
        this.pin_l_bg = pin_l_bg;
        this.pin_r_bg = pin_r_bg;
        this.pin_fg = pin_fg;
        this.needBtns = needBtns;
        this.boxes_bg = boxes_bg;
        this.boxes_fg = boxes_fg;
        this.trans = trans;
        this.captcha_bg = captcha_bg;
    }

    public static CaptchaParametersBean createForDarkTheme(int width) {

        return new CaptchaParametersBean(width,
                                         "16448250",
                                         "16448250",
                                         "6710886",
                                         false,
                                         "4539717",
                                         "12829635",
                                         true,
                                         "16711680");
    }

    public static CaptchaParametersBean createForLightTheme(int width) {

        return new CaptchaParametersBean(width,
                                         "7039851",
                                         "7039851",
                                         "16448250",
                                         false,
                                         "16119287",
                                         "7895160",
                                         true,
                                         "16711680");
    }

    public String getURLParametersPart() {

        String result = "";

        try {
            result = String.format(
                URL_PARAMETERS_PATTERN,
                this.width,
                this.pin_l_bg,
                this.pin_r_bg,
                this.pin_fg,
                this.needBtns,
                this.boxes_bg,
                this.boxes_fg,
                this.trans,
                this.captcha_bg);
        } catch (IllegalFormatException e) {
            LOGGER.error("Error occurred...", e);
        }

        return result;
    }
}
