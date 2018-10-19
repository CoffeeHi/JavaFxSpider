package com.cx.coffeehi.spider.javafx;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;

import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.Data;
import lombok.extern.log4j.Log4j;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.ParseException;

import com.cx.coffeehi.spider.bean.SpiderContext;
import com.cx.coffeehi.spider.utils.SpiderThread;
import com.cx.coffeehi.spider.utils.SpiderUtils;

import javafx.application.Application;
import javafx.event.ActionEvent;

import javafx.scene.control.ProgressBar;

import javafx.scene.control.TextArea;

import javafx.scene.control.CheckBox;

@Data
@Log4j
public class SpiderController implements Initializable {
    private Thread spiderThread;
    private Thread progressThread;
    private SpiderContext spiderContext;
    @FXML
    private Button startSpider;
    @FXML
    private TextField questionId;
    @FXML
    private TextField savePath;
    @FXML
    private TextArea consoleLog;
    @FXML
    private Button stopSpider;
    @FXML
    private CheckBox checkPic;
    @FXML
    private CheckBox checkVideo;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Button getSavePath;

    @FXML
    public void startSpider(ActionEvent event) {
        log.info("START SPIDER");
        setQuestionId();
        SpiderThread.getInstance().mainTaskSubmit(spiderThread);
        SpiderThread.getInstance().scheduleTaskSubmit(progressThread);
    }

    private void setQuestionId() {
        String originQueId = questionId.getText();
        log.info("question id:" + originQueId);
        if (!StringUtils.isEmpty(originQueId)) {
            spiderContext.setQuestionId(originQueId.trim());
        } else {
            spiderContext.setQuestionId("26037846");
        }
    }

    @FXML
    public void stopSpider(ActionEvent event) {
        log.info("STOP SPIDER");
        spiderThread.interrupt();
        SpiderUtils.NOW_NUM.set(0);
        SpiderUtils.TOTAL_NUM.set(0);
        progressBar.setProgress(0);
        SpiderThread.getInstance().close();
    }

    @FXML
    public void checkPic(ActionEvent event) {
        log.info("checkPic:" + checkPic.isSelected());
        spiderContext.setIfCheckVideo(checkPic.isSelected());
    }

    @FXML
    public void checkVideo(ActionEvent event) {
        log.info("checkVideo:" + checkVideo.isSelected());
        spiderContext.setIfCheckVideo(checkVideo.isSelected());
    }

    @FXML
    public void getSavePath(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File file = directoryChooser.showDialog(MainLauncher.getPrimaryStage());
        if (file != null) {
            // 选择的文件夹路径
            String path = file.getPath();
            if (!StringUtils.isEmpty(path)) {
                savePath.setText(path);
                spiderContext.setSavePath(path);
            }
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        spiderContext = new SpiderContext();
        spiderThread = new Thread(() -> SpiderUtils.spiderGo(spiderContext));
        progressThread = new Thread(
            () -> progressBar.setProgress((double) SpiderUtils.NOW_NUM.get() / SpiderUtils.TOTAL_NUM.get()));
    }

}
