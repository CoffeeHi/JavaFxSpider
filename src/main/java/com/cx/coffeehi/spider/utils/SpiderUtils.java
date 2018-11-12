package com.cx.coffeehi.spider.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cx.coffeehi.spider.bean.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.extern.log4j.Log4j;

@Log4j
@lombok.Data
public class SpiderUtils {
    private static String INCLUDE_PARAM =
        "data[*].is_normal,admin_closed_comment,reward_info,is_collapsed,annotation_action,annotation_detail,collapse_reason,is_sticky,collapsed_by,suggest_edit,comment_count,can_comment,content,editable_content,voteup_count,reshipment_settings,comment_permission,created_time,updated_time,review_info,relevant_info,question,excerpt,relationship.is_authorized,is_author,voting,is_thanked,is_nothelp;data[*].mark_infos[*].url;data[*].author.follower_count,badge[*].topics";
    private static String ANSWER_URL = "https://www.zhihu.com/api/v4/questions/questionId/answers";
    private static String VIDEO_URL = "https://lens.zhihu.com/api/videos/videoId";
    public static AtomicLong NOW_NUM = new AtomicLong(0);
    public static AtomicLong TOTAL_NUM = new AtomicLong(0);
    

    private class GetAnswer implements Runnable {
        private SpiderContext spiderContext;
        private String httpUrl;
        private Map<String, String> paramMap;

        public GetAnswer(SpiderContext spiderContext, String httpUrl, Map<String, String> paramMap) {
            this.spiderContext = spiderContext;
            this.httpUrl = httpUrl;
            this.paramMap = paramMap;
        }

        @Override
        public void run() {
            String result = doGet(httpUrl, null, paramMap);
            if (StringUtils.isEmpty(result)) {
                return;
            }
            Result resultObj = JSON.parseObject(result, new TypeReference<Result>() {});
            Paging paging = resultObj.getPaging();
            Long totals = paging.getTotals();
            TOTAL_NUM.set(totals);
            List<Data> dataList = resultObj.getData();
            long timestamp = System.currentTimeMillis();
            for (Data data : dataList) {
                // 每个data是个答案，每个答案里可能存在多个图片或视频
                filterAnswer(data, spiderContext, timestamp);
            }
        }

        /**
         * 回答是否大于1天
         * 
         * @param createTime
         * @param timestamp
         * @return 
         * @author chenxiang
         * @date 2018年11月8日
         */
        private boolean isExceedTime (long createTime, long timestamp) {
            return ((timestamp - createTime) / 1000) * 60 * 60 * 24 > 1;
        }
        
        private void filterAnswer(Data data, SpiderContext spiderContext, long timestamp) {
            // 回答超过1天且赞数小于10，则不提取照片
            if (data.getVoteup_count().longValue() < 10L
                && isExceedTime(data.getCreated_time().longValue(), timestamp)) {
                log.info("Improper answer, the num of answers :" + NOW_NUM.incrementAndGet());
                return;
            }
            String saveDir = spiderContext.getSavePath();
            Author author = data.getAuthor();
            String content = data.getContent();
            Document doc = Jsoup.parse(content);
            boolean isAnonymous = "匿名用户".equals(author.getName());
            String anonymousName = "匿名-" + data.getId();
            String normalName = author.getName() + "-" + data.getId();
            String mediaName = isAnonymous ? anonymousName : normalName;
            int mediaSize = 0;
            Elements videoDocs = null;
            if (spiderContext.isIfCheckVideo()) {
                videoDocs = doc.select("a[class=video-box]");
                mediaSize += videoDocs.size();
            }
            Elements picDocs = null;
            if (spiderContext.isIfCheckPic()) {
                picDocs = doc.select("img[src~=(?i).(png|jpe?g)]");
                mediaSize += picDocs.size();
            }
            if (mediaSize == 0) {
                log.info("No media answer, the num of answers :" + NOW_NUM.incrementAndGet());
                return;
            }
            CountDownLatch mediaLatch = new CountDownLatch(mediaSize);
            if (videoDocs != null) {
                downloadVideo(saveDir, mediaName, videoDocs, mediaLatch);
            }
            if (picDocs != null) {
                downloadPicture(saveDir, mediaName, picDocs, mediaLatch);
            }
            try {
                mediaLatch.await();
                log.info("Downloaded answer, the num of answers :" + NOW_NUM.incrementAndGet());
            } catch (InterruptedException e) {
                log.error("mediaLatch await error : ", e);
            }
        }

        private void downloadPicture(String saveDir, String mediaName, Elements picDocs, CountDownLatch latch) {
            AtomicInteger mediaIndex = new AtomicInteger(1);
            for (Element pic : picDocs) {
                SpiderThread.getInstance().picTaskSubmit(()->{
                        String picUrl = pic.attr("data-original");
                        if (StringUtils.isEmpty(picUrl)) {
                            picUrl = pic.attr("src");
                            String picClass = pic.attr("class");
                            if (StringUtils.isEmpty(picUrl) || !"thumbnail".equals(picClass)) {
                                latch.countDown();
                                return;
                            }
                        }
                        String suffix = picUrl.substring(picUrl.lastIndexOf("."));
                        String picName = mediaName + "-" + mediaIndex.getAndIncrement() + suffix;
                        log.info("picName: " + picName);
                        downloadMedia(picUrl, saveDir, picName);
                        latch.countDown();
                });
            }
        }

        private void downloadVideo(String saveDir, String mediaName, Elements videos, CountDownLatch latch) {
            AtomicInteger mediaIndex = new AtomicInteger(1);
            for (Element video : videos) {
                SpiderThread.getInstance().picTaskSubmit(()-> {
                        String href = video.attr("href");
                        String videoId = href.substring(href.lastIndexOf("/") + 1);
                        String videoTypesUrl = VIDEO_URL.replace("videoId", videoId);
                        log.info("videoTypesUrl: " + videoTypesUrl);
                        String videoTypesDetail = doGet(videoTypesUrl, null, null);
                        log.info("videoTypesDetail: " + videoTypesDetail);
                        VideoDetails videoDetails = JSON.parseObject(videoTypesDetail, new TypeReference<VideoDetails>() {});
                        PlayList playlist = videoDetails.getPlaylist();
                        PlayItem hd = playlist.getHd();
                        if (hd != null) {
                            String videoName = mediaName + "-" + mediaIndex.getAndIncrement() + "." + hd.getFormat();
                            log.info("videoName: " + videoName);
                            log.info(hd.getPlay_url());
                            log.info(saveDir);
                            downloadMedia(hd.getPlay_url(), saveDir, videoName);
                        }
                        latch.countDown();
                });
            }
        }

    }

    public static void spiderGo(SpiderContext spiderContext) {
        String httpUrl = ANSWER_URL.replace("questionId", spiderContext.getQuestionId());
        TOTAL_NUM.set(getPaging(httpUrl));
        int limit = 20;
        int page = 0;
        int offset = 0;
        do {
            offset = (page++) * limit;
            Map<String, String> paramMap = Maps.newHashMap();
            paramMap.put("include", INCLUDE_PARAM);
            paramMap.put("limit", String.valueOf(limit));
            paramMap.put("sort_by", "default");
            paramMap.put("offset", String.valueOf(offset));
            SpiderThread.getInstance()
                    .ansTaskSubmit(new SpiderUtils().new GetAnswer(spiderContext, httpUrl, paramMap));
        } while (offset + limit < TOTAL_NUM.get());
    }

    private static Long getPaging(String httpUrl) {
        Map<String, String> paramMap = Maps.newHashMap();
        paramMap.put("include", "");
        paramMap.put("limit", "0");
        paramMap.put("offset", "0");
        paramMap.put("sort_by", "default");
        String result = doGet(httpUrl, null, paramMap);
        Result resultObj = JSON.parseObject(result, new TypeReference<Result>() {});
        Paging paging = resultObj.getPaging();
        return paging.getTotals();
    }

    private static String doGet(String url, Map<String, String> cookieMap, Map<String, String> paramMap) {
        // 构造cookie
        CookieStore cookieStore = new BasicCookieStore();
        if (cookieMap != null) {
            for (Entry<String, String> entry : cookieMap.entrySet()) {
                cookieStore.addCookie(new BasicClientCookie(entry.getKey(), entry.getValue()));
            }
        }
        // 构造Headers
        List<Header> headerList = Lists.newArrayList();
        // headerList.add(new BasicHeader(HttpHeaders.ACCEPT_ENCODING,"gzip, deflate, br"));
        // headerList.add(new BasicHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 6.1; WOW64)
        // AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"));
        headerList.add(new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9"));
        // headerList.add(new BasicHeader(HttpHeaders.CONNECTION, "keep-alive"));
        // headerList.add(new BasicHeader("x-udid", "AAAotaVyWA6PTheLYUy_KuTRJ9U_RwAH7yM="));
        // headerList.add(new BasicHeader("x-requested-with", "fetch"));
        headerList.add(new BasicHeader("content-type", "application/json"));
        // 构造传参
        List<NameValuePair> paramList = Lists.newArrayList();
        if (paramMap != null) {
            for (Entry<String, String> elem : paramMap.entrySet()) {
                paramList.add(new BasicNameValuePair(elem.getKey(), elem.getValue()));
            }
            if (!paramList.isEmpty()) {
                String str = "";
                try {
                    str = EntityUtils.toString(new UrlEncodedFormEntity(paramList, Consts.UTF_8));
                } catch (Exception e) {
                    log.error("UrlEncodedFormEntity ERROR: ", e);
                }
                url = url + "?" + str;
            }
        }
        String result = null;
        HttpGet httpGet = new HttpGet(url);
        int tryTimes = 3;
        while (tryTimes-- > 0) {
            try (
                CloseableHttpClient httpClient =
                    HttpClients.custom().setDefaultHeaders(headerList).setDefaultCookieStore(cookieStore).build();
                CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    continue;
                }
                log.info("HTTP STATUS : " + response.getStatusLine());
                HttpEntity entity = response.getEntity();
                Header contentEncoding = entity.getContentEncoding();
                if (contentEncoding != null) {
                    String encode = contentEncoding.getValue();
                    if ("gzip".equalsIgnoreCase(encode)) {
                        entity = new GzipDecompressingEntity(entity);
                    } else if ("deflate".equalsIgnoreCase(encode)) {
                        entity = new DeflateDecompressingEntity(entity);
                    } else if ("br".equalsIgnoreCase(encode)) {
                        log.info("fuck br");
                    }
                }
                result = EntityUtils.toString(entity, Consts.UTF_8);
                break;
            } catch (Exception e) {
                log.error("HTTP URL: " + url);
                log.error("DO GET ERROR: ", e);
            }
        }
        return result;
    }


    /**
     * @param: [mediaUrl, saveDir, mediaName]
     * @return: void
     * @auther: CoffeeHi
     * @date: 2018/10/23 0023 上午 12:44
     */
    private static void downloadMedia(String mediaUrl, String saveDir, String mediaName) {
        HttpGet get = new HttpGet(mediaUrl);
        String absolutePath = saveDir + "\\" + mediaName;
        log.debug("FilePath: " + absolutePath);
        File file = new File(absolutePath);
        int tryTimes = 3;
        while (tryTimes-- > 0) {
            try (CloseableHttpClient httpclient = HttpClients.createDefault();
                CloseableHttpResponse response = httpclient.execute(get);
                FileOutputStream fout = new FileOutputStream(file)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    continue;
                }
                HttpEntity entity = response.getEntity();
                InputStream in = entity.getContent();
                if (!file.exists()) {
                    file.createNewFile();
                } else {
                    log.debug("contentLength: " + entity.getContentLength());
                    log.debug("fileName: " + file.getName() + ", fileExistedLength: " + file.length());
                    if (entity.getContentLength() == file.length()) {
                        return;
                    }
                }
                int len;
                byte[] tmp = new byte[1024];
                while ((len = in.read(tmp)) != -1) {
                    fout.write(tmp, 0, len);
                }
                fout.flush();
                break;
            } catch (Exception e) {
                log.error("下载出错：" + absolutePath);
                log.error("下载出错：" + mediaUrl);
                log.error("下载出错", e);
            }
        }
    }

}
