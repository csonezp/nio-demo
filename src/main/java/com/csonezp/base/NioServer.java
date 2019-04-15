// Copyright (C) 2019 Meituan
// All rights reserved
package com.csonezp.base;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author zhangpeng34
 * Created on 2019/4/15 下午3:03
 **/
public class NioServer {
    private static final int PORT = 8081;

    public static void main(String[] args) throws IOException {
        //创建新的selector
        Selector selector = Selector.open();

        ServerSocketChannel ssc = init(selector);

        while (true) {
            //查询是否有事件就绪
            int ops = selector.select(10);
            if (ops > 0) {
                //查出所有的就绪的key
                Set<SelectionKey> readyKeySet = selector.selectedKeys();
                Iterator<SelectionKey> iterator = readyKeySet.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        //此时来了一个就绪的新连接
                        accept(key);
                    } else if (key.isReadable()) {
                        //此时key可以进行读取操作，且有key注册了OP_READ。
                        //注意isReadable 不是说你的读取内容操作已经完成，而是该key对应的channel可以进行读取操作了，
                        //内容还是需要用代码读取从channel出来的
                        read(key);
                    } else if (key.isWritable()) {
                        //此时key可以进行写操作，且有key注册了OP_WRITE
                        //注意同上
                        write(key);
                    }
                }

            }
        }
    }


    /**
     * 处理新请求连接就绪
     */
    private static void accept(SelectionKey key) throws IOException {
        System.out.println("handle accept");
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        //拿到该请求的SocketChannel
        SocketChannel sc = serverSocketChannel.accept();
        //设置为非阻塞
        sc.configureBlocking(false);
        //重新注册key.此时该请求的连接已经处理完毕，需要注册OP_READ，来进行下一步的读取内容操作
        //注意是SocketChannel注册OP_READ，而不是ServerSocketChannel
        //ServerSocketChannel是服务器的channel，只需要注册OP_ACCEPT来接收请求即可
        sc.register(key.selector(), SelectionKey.OP_READ);
    }

    /**
     * 处理可读
     */
    private static void read(SelectionKey key) throws IOException {
        StringBuffer sb = new StringBuffer();

        SocketChannel socketChannel = (SocketChannel) key.channel();
        //创建一个大小为1024k的buffer
        //实际生产一般不会直接创建新的buffer，也需要考虑半包、粘包问题，这里不做讨论
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        //读取channel的数据，写入到buffer
        int count = socketChannel.read(buffer);

        if (count > 0) {
            //翻转缓存区(将缓存区由写进数据模式变成读出数据模式)
            buffer.flip();
            //将缓存区的数据转成String
            sb.append(new String(buffer.array(), 0, count));
        }
        if (sb.length() > 0) {
            //req就是客户端的请求
            String req = sb.toString();
            System.out.println("handle read:" + req);
            //处理请求，获取响应
            String resp = handlerReq(req);
            //注册OP_WRITE,等待可写后进行返回响应操作
            //resp作为att参数，可以跟随key传递
            socketChannel.register(key.selector(), SelectionKey.OP_WRITE, resp);
        }

    }


    private static void write(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        //获取之前的read事件中
        String resp = (String) key.attachment();
        key.attach("");
        //如果没有需要写出的resp，则忽略
        if (resp == null || resp.equals("")) {
            return;
        }
        System.out.println("handle write:" + resp);

        //写到channel中
        sc.write(ByteBuffer.wrap(resp.getBytes()));

        //如果响应是关闭命令，则关闭连接
        if (resp.equalsIgnoreCase("close") || resp.equalsIgnoreCase("close\n")) {
            key.cancel();
            sc.socket().close();
            sc.close();
            System.out.println("连接断开......");
            return;
        }
        //如果不是关闭，则重设此OP_READ兴趣，等待下一次输入
        sc.register(key.selector(), SelectionKey.OP_READ);

    }

    /**
     * 初始化ssc和selector,并将ssc注册到selector上
     */
    private static ServerSocketChannel init(Selector selector) throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(PORT));
        ssc.configureBlocking(false);
        //将ssc注册到selector上，并同时设置对OP_ACCEPT事件感兴趣
        ssc.register(selector, SelectionKey.OP_ACCEPT);
        return ssc;
    }

    //模拟处理请求
    private static String handlerReq(String req) {
        if(req.equals("close")||req.equals("close\n")){
            return "close";
        }
        return "Hello!" + req;
    }
}