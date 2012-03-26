package me.bcap.sandbox.netty.echo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Log4JLoggerFactory;

public class EchoServer implements Runnable {

	private static final Logger logger = Logger.getLogger(EchoServer.class);

	private ChannelGroup channelGroup = new DefaultChannelGroup("EchoServer");

	@Override
	public void run() {
		ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

		final ChannelHandler stringEncoder = new StringEncoder(Charset.forName("UTF-8"));
		final ChannelHandler stringDecoder = new StringDecoder(Charset.forName("UTF-8"));
		final ChannelHandler loggingHandler = new LoggingHandler();

		ServerBootstrap bootstrap = new ServerBootstrap(factory);

		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				pipeline.addLast("logger", loggingHandler);
				pipeline.addLast("framer", new DelimiterBasedFrameDecoder(512, true, Delimiters.lineDelimiter()));
				pipeline.addLast("decoder", stringDecoder);
				pipeline.addLast("encoder", stringEncoder);
				pipeline.addLast("echo", new EchoServerHandler());
				return pipeline;
			}
		});

		bootstrap.setOption("child.tcpNoDelay", true);
		bootstrap.setOption("child.keepAlive", true);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.debug("Shutting down server");
				channelGroup.close().awaitUninterruptibly();
				logger.debug("Server shutted down");
			}
		});

		int port = 8080;

		logger.debug("binding to port " + port);

		channelGroup.add(bootstrap.bind(new InetSocketAddress(port)));

		logger.info("bound to port " + port);
	}

	public static void main(String[] args) throws Exception {
		BasicConfigurator.configure();
		InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory());
		new EchoServer().run();
	}
}

class EchoServerHandler extends SimpleChannelHandler {

	private static final Logger logger = Logger.getLogger(EchoServer.class);

	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws IOException {
		String message = (String) e.getMessage();
		logger.info("echoing received message: " + message);
		e.getChannel().write("echo -> " + message + "\n");
	}

	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
		logger.error(e.getCause().getMessage(), e.getCause());
		e.getChannel().close();
	}
}