// Copyright (C) 2019 Meituan
// All rights reserved
package com.csonezp.base;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

/**
 * @author zhangpeng34
 * Created on 2019/4/15 下午3:47
**/ 
public class NioClient {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8081;
    private static SocketChannel sc;

    private static volatile boolean flag = true;

    public static void main(String[] args) throws IOException, InterruptedException {
        sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.connect(new InetSocketAddress(HOST, PORT));

        while (flag){
            Scanner in = new Scanner(System.in);
            System.out.println("说点儿什么吧....");
            String req = in.nextLine();
            String response = chat(req);
            System.out.println(response);
        }

    }

    private static String chat(String req) {
        sendMsg(req);
        String resp = null;

        while (true) {
            resp = receiveMsg();
            if(StringUtils.isNotBlank(resp)){
                break;
            }
        }

        return resp;
    }

    public static void sendMsg(String msg) {
        try {
            while (!sc.finishConnect()) {
                System.exit(0);
            }
            sc.write(ByteBuffer.wrap(msg.getBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String receiveMsg() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.clear();
        StringBuffer sb = new StringBuffer();
        int count = 0;
        String msg = null;
        try {
            while ((count = sc.read(buffer)) > 0) {
                sb.append(new String(buffer.array(), 0, count));
            }
            if (sb.length() > 0) {
                msg = sb.toString();
                if ("close".equals(sb.toString()) || "close\n".equalsIgnoreCase(msg)) {
                    msg = null;
                    sc.close();
                    sc.socket().close();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return msg;
    }
}