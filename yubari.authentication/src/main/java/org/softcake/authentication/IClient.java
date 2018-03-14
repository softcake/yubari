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

import com.dukascopy.api.IChart;
import com.dukascopy.api.IClientGUI;
import com.dukascopy.api.IClientGUIListener;
import com.dukascopy.api.INewsFilter;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.IFeedInfo;
import com.dukascopy.api.instrument.IFinancialInstrument;
import com.dukascopy.api.plugins.Plugin;
import com.dukascopy.api.plugins.PluginGuiListener;
import com.dukascopy.api.strategy.remote.IRemoteStrategyManager;
import com.dukascopy.api.system.IPreferences;
import com.dukascopy.api.system.IStrategyExceptionHandler;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.JFAuthenticationException;
import com.dukascopy.api.system.JFVersionException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main entry point for the connection to the server
 *
 * @author Denis Larka, Dmitry Shohov
 */
public interface IClient {	

    /**
     * Authenticates and connects to dukascopy servers
     *
     * @param jnlp address of jnlp file, that is used to launch platform
     * @param username user name
     * @param password password
     * @throws JFAuthenticationException authentication error, incorrect username or password,
     *      IP address unrecognized in case of LIVE systems
     * @throws JFVersionException version is blocked on the server, update your libraries
     * @throws Exception all kinds of errors that resulted in exception
     */
    void connect(String jnlp, String username, String password) throws
                                                                JFAuthenticationException, JFVersionException, Exception;

    /**
     * Authenticates and connects to dukascopy servers
     *
     * @param jnlp address of jnlp file, that is used to launch platform
     * @param username user name
     * @param password password
     * @param pin pin code generated with the captcha from the last {@link #getCaptchaImage} call
     * @throws JFAuthenticationException authentication error, incorrect username or password,
     *      IP address unrecognized in case of LIVE systems
     * @throws JFVersionException version is blocked on the server, update your libraries
     * @throws Exception all kinds of errors that resulted in exception
     */
    void connect(String jnlp, String username, String password, String pin) throws JFAuthenticationException, JFVersionException, Exception;
    
    /**
     * Returns the image that can be provided to the user to generate correct pin code
     *
     * @param jnlp address of jnlp file, that is used to launch platform
     * @return captcha image
     * @throws Exception if request for captcha failed
     */
    BufferedImage getCaptchaImage(String jnlp) throws Exception;

    /**
     * Tries to reconnect transport without reauthenticating. Method is asynchronous, meaning it will exit immidiately
     * after sending connection request without waiting for the response. Caller will receive notification through
     * {@link ISystemListener} interface
     */
    void reconnect();
    
    /**
     * Stops all running strategies and disconnect from dukascopy server.
     */
    void disconnect();

    /**
     * Returns true if client is authenticated authorized and transport is in connected state
     *
     * @return true if there is open and working connection to the server
     */
    boolean isConnected();

    /**
     * Starts the strategy with default exception handler that will stop strategy if it trows exception
     *
     * @param strategy strategy to run
     * @return returns id assigned to the strategy
     * @throws IllegalStateException if not connected
     * @throws NullPointerException if one of the parameters is null
     */
    long startStrategy(IStrategy strategy) throws IllegalStateException, NullPointerException;

    /**
     * Starts the strategy
     *
     * @param strategy strategy to run
     * @param exceptionHandler if not null then passed exception handler will be called when strategy throws exception
     * @return returns id assigned to the strategy
     * @throws IllegalStateException if not connected
     * @throws NullPointerException if one of the parameters is null
     */
    long startStrategy(IStrategy strategy, IStrategyExceptionHandler exceptionHandler) throws IllegalStateException, NullPointerException;

    /**
     * Starts the plugin
     *
     * @param plugin plugin to run
     * @param exceptionHandler if not null then passed exception handler will be called when strategy throws exception
     * @return returns id assigned to the strategy
     * @throws IllegalStateException if not connected
     * @throws NullPointerException if one of the parameters is null
     */
    UUID runPlugin(Plugin plugin, IStrategyExceptionHandler exceptionHandler) throws IllegalStateException, NullPointerException;
    
    /**
     * Starts the strategy
     *
     * @param plugin plugin to run
     * @param exceptionHandler if not null then passed exception handler will be called when strategy throws exception
     * @param pluginGuiListener listener for plugin gui events
     * @return returns id assigned to the strategy
     * @throws IllegalStateException if not connected
     * @throws NullPointerException if one of the parameters is null
     */
    UUID runPlugin(Plugin plugin, IStrategyExceptionHandler exceptionHandler, PluginGuiListener pluginGuiListener) throws IllegalStateException, NullPointerException;
    
    /**
     * Stops the strategy with the specified id
     *
     * @param processId id of the strategy
     */
    void stopPlugin(UUID processId);

    /**
     * Loads strategy from jfx file
     *
     * @param strategyBinaryFile jfx file
     * @return loaded strategy
     * @throws Exception if loading failed
     */
    IStrategy loadStrategy(File strategyBinaryFile) throws Exception;

    /**
     * Stops the strategy with the specified id
     *
     * @param processId id of the strategy
     */
    void stopStrategy(long processId);

    /**
     * Returns map with ids mapped to associated strategies.
     * Includes only strategies started by the current process.
     * 
     * @return started strategies
     */
    Map<Long, IStrategy> getStartedStrategies();

    /**
     * Returns map with ids mapped to associated strategies
     * 
     * @return started strategies
     */
    Map<UUID, Plugin> getRunningPlugins();

    /**
     * Sets the listener, that will receive notifications about connects disconnects and strategies starts and stops.
     * Only one system listener can be set.
     *
     * @param systemListener listener
     */
    void setSystemListener(ISystemListener systemListener);
   
    /**
     * Adds news filter
     *
     * @param newsFilter news filter
     */
    void addNewsFilter(INewsFilter newsFilter);

    /**
     * Returns news filter for the source
     *
     * @param newsSource news source
     * @return news filter
     */
    INewsFilter getNewsFilter(INewsFilter.NewsSource newsSource);

    /**
     * Removes news filter, resetting it to the default value
     *
     * @param newsSource news source
     * @return news filter removed
     */
    INewsFilter removeNewsFilter(INewsFilter.NewsSource newsSource);

    /**
     * Subscribes to the specified instruments set.
     * Ticks passed in onTick method will have full depth for this instruments, while other instruments are not guaranteed to have full depth.
     *
     * @param instruments set of the instruments
     */
    void setSubscribedInstruments(Set<Instrument> instruments);

    /**
     * Unsubscribes from instruments specified in instruments set.
     *
     * @param instruments set of the instruments
     */
    void unsubscribeInstruments(final Set<Instrument> instruments);

    /**
     * Subscribes to the specified instruments set.
     * Ticks passed in onTick method will have full depth for this instruments, while other instruments are not guaranteed to have full depth.
     *
     * @param financialInstruments set of the instruments
     */
    @Deprecated
    void setSubscribedFinancialInstruments(Set<IFinancialInstrument> financialInstruments);

    /**
     * Returns subscribed instruments
     *
     * @return set of the subscribed instruments
     */
    Set<Instrument> getSubscribedInstruments();

    /**
     * Returns subscribed instruments
     *
     * @return set of the subscribed instruments
     */
    @Deprecated
    Set<IFinancialInstrument> getSubscribedFinancialInstruments();

    /**
     * Sets stream that will be passed to the strategy through IConsole. Default out is System.out
     *
     * @param out stream
     */
    void setOut(PrintStream out);

    /**
     * Sets stream that will be passed to the strategy through IConsole. Default err is System.err
     *
     * @param err stream
     */
    void setErr(PrintStream err);

    /**
     * Sets the location of the cache files. Default is <code>System.getProperty("java.io.tmpdir") + "/.cache"</code>
     * <p> The method should get called <u>before</u> the call of {@link #connect(String, String, String)}.
     * <p>WARNING: JForex might delete all folder's content if folder already existed AND was not created by this method call.  
     *
     * @param cacheDirectory directory where cache files should be saved
     */
    void setCacheDirectory(File cacheDirectory);
    
    /**
     * Compile .java strategy file to .jfx file. Destination .jfx file will be located in the same directory as the source.
     *
	 * @param srcJavaFile .java source file to be compiled
	 * @param obfuscate if true, the strategy will be obfuscated
     * @throws IllegalStateException if not connected
     */
    void compileStrategy(String srcJavaFile, boolean obfuscate);
    
    /**
     * @see #packToJfx(File)
     * 
     * @param file .jar source file to be packed
     * @return the packed jfx file.
     * @throws IllegalStateException if not connected
     */
    File packPluginToJfx(File file);
    
    /**
     * Packs .jar a strategy, indicator or plugin file to .jfx file. 
     * Destination .jfx file will be located in the same directory as the source.
     * The META-INF/MANIFEST.MF should contain an entry which determines the qualified name
     * of the strategy, indicator or plugin main class (the class which will serve
     * as the "entry point" for the platform. For instance:
     * <p>
     * <tt>jfMainClass: mypackage.MyStrategy</tt>
     *
     * @param file .jar source file to be packed
     * @return the packed jfx file.
     * @throws IllegalStateException if not connected
     */
    File packToJfx(File file);

    /**
     * Register a {@link IClientGUIListener} which is "interested" in open / close charts from strategy
     * @param clientGuiListener {@link IClientGUIListener}
     */
    void addClientGUIListener(IClientGUIListener clientGuiListener);
    
    /**
     * Unregister a {@link IClientGUIListener}
     * @param clientGuiListener {@link IClientGUIListener}
     */
    void removeClientGUIListener(IClientGUIListener clientGuiListener);

    /**
     * Opens new chart with specified in {@link IFeedDescriptor} parameters.
     * 
     * @param feedDescriptor Feed descriptor for chart to open
     * @throws IllegalArgumentException when feedDescriptor not formed well
     * @return IChart newly created chart
     */
    IChart openChart(IFeedDescriptor feedDescriptor);

    /**
     * Opens new chart with specified in {@link IFeedInfo} parameters.
     *
     * @param feedInfo Feed info for chart to open
     * @throws IllegalArgumentException when feedDescriptor not formed well
     * @return IChart newly created chart
     */
    @Deprecated
    IChart openChart(IFeedInfo feedInfo);

    /**
     * Opens new chart from specified .tmpl file
     *
     * @param chartTemplate File file to open
     * @throws IllegalArgumentException when file is not found or valid
     * @return IChart newly created chart
     */
    IChart openChart(File chartTemplate);
    
    /**
     * Close specified chart.
     * 
     * @param chart Chart to close
     */
    void closeChart(IChart chart);
    
    /**
     * Returns {@link IClientGUI} for specified {@link IChart}.<br/>
     * <b>NOTE:</b> Can be used only by <b>Standalone JForex API</b>. There is no access to embedded chart's panels from  JForex Platform.
     * @param chart {@link IChart} 
     * @return {@link IClientGUI} for specified {@link IChart}, or <tt>null</tt> if it's not Standalone JForex API environment.
     */
    IClientGUI getClientGUI(IChart chart);

    /**
     * Returns a set of operable for the current user instruments.
     * Use this method to get all available instruments. If there is no 
     * instruments available, the method return empty set. 
     *  
     * @return Set<Instrument> of available instruments
     */ 
	Set<Instrument> getAvailableInstruments();

    /**
     * Returns a set of operable for the current user instruments.
     * Use this method to get all available instruments. If there is no
     * instruments available, the method return empty set.
     *
     * @return Set<IFinancialInstrument> of available instruments
     */
    @Deprecated
    Set<IFinancialInstrument> getAvailableFinancialInstruments();
	
    /**
     * Returns global preference holder for JForex-SDK
     * 
     * @return global preference holder for JForex-SDK
     */
    IPreferences getPreferences();
    
    /**
     * Sets global preferences for JForex-SDK
     * Consider hiding the position labels and the closed positions from charts (needs to be called after a successful connection):
     * <pre>
     * IPreferences pref = client
     *     .getPreferences().chart().orders()
     *           .entryOrders(true)
     *     .preferences().chart().positions()
     *           .positionExternalIdLabels(false)
     *           .closedPositions(false)
     *           .openPositions(true)
     *     .preferences();
     * client.setPreferences(pref);
     * </pre>
     * 
     */
    void setPreferences(IPreferences preferences);

    /**
     * Returns a manager for remote strategy running, stopping and monitoring
     * 
     * @return a manager for remote strategy running, stopping and monitoring
     */
    IRemoteStrategyManager getRemoteStrategyManager();
}
