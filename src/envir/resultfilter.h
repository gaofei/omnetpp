//==========================================================================
//  RESULTFILTER.H - part of
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//  Author: Andras Varga
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2008 Andras Varga
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __ENVIR_RESULTFILTER_H
#define __ENVIR_RESULTFILTER_H

#include "envirdefs.h"
#include "resultlistener.h"
#include "stringpool.h"
#include "onstartup.h"
#include "cregistrationlist.h"
#include "cownedobject.h"

class ResultFilter;

#define Register_ResultFilter(NAME, CLASSNAME) \
  static ResultFilter *__FILEUNIQUENAME__() {return new CLASSNAME;} \
  EXECUTE_ON_STARTUP(resultFilters.getInstance()->add(new ResultFilterDescriptor(NAME,__FILEUNIQUENAME__));)

extern cGlobalRegistrationList resultFilters;

/**
 * Base class for result filters. Result filters map ONE SIGNAL to ONE SIGNAL
 * (i.e. vector-to-vector one-to-one mapping), and accept several listeners
 * (delegates). Result filters do not record anything -- that is left to result
 * recorders.
 */
class ENVIR_API ResultFilter : public ResultListener
{
    private:
        ResultListener **delegates; // NULL-terminated array
    protected:
        void fire(ResultFilter *prev, long l);
        void fire(ResultFilter *prev, double d);
        void fire(ResultFilter *prev, simtime_t t, double d);
        void fire(ResultFilter *prev, simtime_t t);
        void fire(ResultFilter *prev, const char *s);
        void fire(ResultFilter *prev, cObject *obj);
    public:
        ResultFilter();
        ~ResultFilter();
        virtual void addDelegate(ResultListener *delegate);
        virtual int getNumDelegates() const;
        std::vector<ResultListener*> getDelegates() const;
        virtual void finish(ResultFilter *prev);
};

class ENVIR_API NumericResultFilter : public ResultFilter
{
    protected:
        // all receiveSignal() methods either throw error or delegate here;
        // return value: whether to invoke chained listeners (true) or to swallow the value (false)
        virtual bool process(double& value) = 0;
        virtual bool process(simtime_t& t, double& value) = 0;
    public:
        virtual void receiveSignal(ResultFilter *prev, long l);
        virtual void receiveSignal(ResultFilter *prev, double d);
        virtual void receiveSignal(ResultFilter *prev, simtime_t t, double d);
        virtual void receiveSignal(ResultFilter *prev, simtime_t t);
        virtual void receiveSignal(ResultFilter *prev, const char *s);
        virtual void receiveSignal(ResultFilter *prev, cObject *obj);
};

/**
 * Registers a ResultFilter.
 */
class ENVIR_API ResultFilterDescriptor : public cNoncopyableOwnedObject
{
  private:
    ResultFilter *(*creatorfunc)();

  public:
    /**
     * Constructor.
     */
    ResultFilterDescriptor(const char *name, ResultFilter *(*f)());

    /**
     * Creates an instance of a particular class by calling the creator
     * function.
     */
    ResultFilter *create() const  {return creatorfunc();}

    /**
     * Finds the factory object for the given name. The class must have been
     * registered previously with the Register_ResultFilter() macro.
     */
    static ResultFilterDescriptor *find(const char *name);

    /**
     * Like find(), but throws an error if the object was not found.
     */
    static ResultFilterDescriptor *get(const char *name);
};

#endif


