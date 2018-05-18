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

package org.softcake.yubari.netty.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link ChannelInboundHandlerAdapter} which allows to explicit only handle a specific type of messages.
 * <p>
 * For example here is an implementation which only handle {@link String} messages.
 *
 * <pre>
 *     public class StringHandler extends
 *             {@link FilteredChannelInboundHandler}&lt;{@link String}&gt; {
 *
 *         {@code @Override}
 *         protected void channelRead0({@link ChannelHandlerContext} ctx, {@link String} message)
 *                 throws {@link Exception} {
 *             System.out.println(message);
 *         }
 *     }
 * </pre>
 * <p>
 * Be aware that depending of the constructor parameters it will release all handled messages by passing them to
 * {@link ReferenceCountUtil#release(Object)}. In this case you may need to use
 * {@link ReferenceCountUtil#retain(Object)} if you pass the object to the next handler in the {@link ChannelPipeline}.
 *
 * <h3>Forward compatibility notice</h3>
 * <p>
 * Please keep in mind that {@link #channelRead0(ChannelHandlerContext, I)} will be renamed to
 * {@code messageReceived(ChannelHandlerContext, I)} in 5.0.
 * </p>
 */
public abstract class FilteredChannelInboundHandler<I> extends ChannelInboundHandlerAdapter {

    private final TypeParameterMatcher[] matcher;
    private final boolean autoRelease;

    /**
     * see {@link #FilteredChannelInboundHandler(boolean)} with {@code true} as boolean parameter.
     */
    protected FilteredChannelInboundHandler() {

        this(true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param autoRelease {@code true} if handled messages should be released automatically by passing them to
     *                    {@link ReferenceCountUtil#release(Object)}.
     */
    protected FilteredChannelInboundHandler(boolean autoRelease) {

        matcher = new TypeParameterMatcher[1];
        matcher[0] = TypeParameterMatcher.find(this, FilteredChannelInboundHandler.class, "I");
        this.autoRelease = autoRelease;
    }


    /**
     * see {@link #FilteredChannelInboundHandler(Class[], boolean)} with {@code true} as boolean value.
     */
    protected FilteredChannelInboundHandler(Class<? extends I>[] inboundMessageTypes) {

        this(inboundMessageTypes, true);
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageTypes The type of messages to match
     * @param autoRelease        {@code true} if handled messages should be released automatically by passing them to
     *                           {@link ReferenceCountUtil#release(Object)}.
     */
    protected FilteredChannelInboundHandler(Class<? extends I>[] inboundMessageTypes, boolean autoRelease) {

        matcher = new TypeParameterMatcher[inboundMessageTypes.length];

        for (int i = 0; i < inboundMessageTypes.length; i++) {
            matcher[i] = TypeParameterMatcher.get(inboundMessageTypes[i]);
        }
        this.autoRelease = autoRelease;
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptInboundMessage(Object msg) throws Exception {

        for (int i = 0; i < matcher.length; i++) {
            if (matcher[i].match(msg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        boolean release = true;
        try {
            if (acceptInboundMessage(msg)) {
                @SuppressWarnings("unchecked") I imsg = (I) msg;
                channelRead0(ctx, imsg);
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (autoRelease && release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    /**
     * <strong>Please keep in mind that this method will be renamed to
     * {@code messageReceived(ChannelHandlerContext, I)} in 5.0.</strong>
     * <p>
     * Is called for each message of type {@link I}.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link FilteredChannelInboundHandler}
     *            belongs to
     * @param msg the message to handle
     * @throws Exception is thrown if an error occurred
     */
    protected abstract void channelRead0(ChannelHandlerContext ctx, I msg) throws Exception;
}
