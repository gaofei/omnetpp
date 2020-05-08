/*--------------------------------------------------------------*
  Copyright (C) 2006-2020 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.pychart;

import py4j.Py4JException;

/**
 * The main entry point to a spawned Python process from Java.
 * Used to check if the connection works, to pass additional references of
 * Java objects to Python, and to execute arbitrary Python code (chart scripts).
 *
 * There are no Java implementations, instead, there is a Python implementation
 * (through Py4J) in PythonEntryPoint.py.
 */
public interface IPythonEntryPoint {

    /**
     * Dummy function, only used to poll if we have a working Py4J connection:
     * that the Python process got to the point where a Gateway server is
     * listening, and ready to execute method calls.
     */
    boolean check();

    /**
     * Sets the result provider of the associated Python process to
     * resultsProvider. A result provider is needed by the omnetpp.scave.results
     * Python module to access simulation result items in ResultFileManager.
     */
    void setResultsProvider(IScaveResultsPickleProvider resultsProvider);

    /**
     * Sets the chart provider of the associated Python process to
     * chartProvider. A chart provider is needed by the omnetpp.scave.chart
     * Python module to access a Chart object.
     */
    void setChartProvider(IChartProvider chartProvider);

    /**
     * Sets the provider of the associated Python process to
     * chartProvider. A chart provider is needed by the omnetpp.scave.chart
     * Python module to access a Chart object.
     */
    void setPlotWidgetProvider(IPlotWidgetProvider widgetProvider);
    void setNativeChartPlotter(INativeChartPlotter chartPlotter);

    /**
     * Can be used to set arbitrary data in the execution context of the
     * Python scripts passed to execute(), in the form of a pickle.
     */
    void setGlobalObjectPickle(String name, byte[] pickle);

    /**
     * Executes any Python code given in command, using exec()
     */
    void execute(String command) throws Py4JException;
}
