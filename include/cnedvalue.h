//==========================================================================
//   CNEDVALUE.H  - part of
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

#ifndef __CNEDVALUE_H
#define __CNEDVALUE_H

#include <string>
#include "simkerneldefs.h"
#include "cexception.h"

NAMESPACE_BEGIN

class cPar;
class cXMLElement;
class cDynamicExpression;

/**
 * Value used during evaluating NED expressions.
 *
 * See notes below.
 *
 * <b>Double vs long:</b>
 *
 * There is no <tt>long</tt> field in cNEDValue: all numeric calculations are
 * performed in floating point (<tt>double</tt>). While this is fine on 32-bit
 * architectures, on 64-bit architectures some precision will be lost
 * because IEEE <tt>double</tt>'s mantissa is only 53 bits.
 *
 * <b>Measurement unit strings:</b>
 *
 * For performance reasons, the functions that store a measurement unit
 * will only store the <tt>const char *</tt> pointer and not copy the
 * string itself. Consequently, the passed unit pointers must stay valid
 * at least during the lifetime of the cNEDValue object, or even longer
 * if the same pointer propagates to other cNEDValue objects. It is recommended
 * that you only pass pointers that stay valid during the entire simulation.
 * It is safe to use: (1) string constants from the code; (2) units strings
 * from other cNEDValues; and (3) stringpooled strings, see cStringPool.
 *
 * @see cDynamicExpression, cNEDFunction, Define_NED_Function()
 * @ingroup EnumsTypes
 */
class SIM_API cNEDValue
{
    friend class cDynamicExpression;
  public:
    /**
     * Type of the value stored in a cNEDValue object.
     */
    // Note: char codes need to be present and be consistent with cNEDFunction::getArgTypes()!
    enum Type {UNDEF=0, BOOL='B', DBL='D', STR='S', XML='X'} type;

  private:
    bool bl;
    double dbl;
    const char *dblunit; // string constants or pooled strings; may be NULL
    std::string s;
    cXMLElement *xml;

  private:
#ifdef NDEBUG
    void assertType(Type) const {}
#else
    void assertType(Type t) const {if (type!=t) cannotCastError(t);}
#endif
    void cannotCastError(Type t) const;

  public:
    /** @name Constructors */
    //@{
    cNEDValue()  {type=UNDEF;}
    cNEDValue(bool b)  {set(b);}
    cNEDValue(long l)  {set(l);}
    cNEDValue(double d)  {set(d);}
    cNEDValue(double d, const char *unit)  {set(d,unit);}
    cNEDValue(const char *s)  {set(s);}
    cNEDValue(const std::string& s)  {set(s);}
    cNEDValue(cXMLElement *x)  {set(x);}
    cNEDValue(const cPar& par) {set(par);}
    //@}

    /**
     * Assignment
     */
    void operator=(const cNEDValue& other);

    /** @name Type, unit conversion and misc. */
    //@{
    /**
     * Returns the value type.
     */
    Type getType() const  {return type;}

    /**
     * Returns the given type as a string.
     */
    static const char *getTypeName(Type t);

    /**
     * Returns true if the stored value is of a numeric type.
     */
    bool isNumeric() const {return type==DBL;}

    /**
     * Returns true if the value is not empty, i.e. type is not UNDEF.
     */
    bool isSet() const  {return type!=UNDEF;}

    /**
     * Returns the value in text form.
     */
    std::string str() const;

    /**
     * Convert the given number into the target unit (e.g. milliwatt to watt).
     * Throws an exception if conversion is not possible (unknown/unrelated units).
     *
     * @see convertTo(), doubleValueInUnit(), setUnit()
     */
    static double convertUnit(double d, const char *unit, const char *targetUnit);
    //@}

    /** @name Setter functions. Note that overloaded assignment operators also exist. */
    //@{

    /**
     * Sets the value to the given bool value.
     */
    void set(bool b) {type=BOOL; bl=b;}

    /**
     * Sets the value to the given long value.
     */
    void set(long l) {type=DBL; dbl=l; dblunit=NULL;}

    /**
     * Sets the value to the given double value and measurement unit.
     * The unit string pointer is expected to stay valid during the entire
     * duration of the simulation (see related class comment).
     */
    void set(double d, const char *unit=NULL) {type=DBL; dbl=d; dblunit=unit;}

    /**
     * Sets the value to the given double value, preserving the current
     * measurement unit. The object must already have the DBL type.
     */
    void setPreservingUnit(double d) {assertType(DBL); dbl=d;}

    /**
     * Sets the measurement unit to the given value, leaving the numeric part
     * of the quantity unchanged. The object must already have the DBL type.
     * The unit string pointer is expected to stay valid during the entire
     * duration of the simulation (see related class comment).
     */
    void setUnit(const char *unit) {assertType(DBL); dblunit=unit;}

    /**
     * Permanently converts this value to the given unit. The value must
     * already have the type DBL. If the current unit cannot be converted
     * to the given one, an error will be thrown. The unit string pointer
     * is expected to stay valid during the entire simulation (see related
     * class comment).
     *
     * @see doubleValueInUnit()
     */
    void convertTo(const char *unit);

    /**
     * Sets the value to the given string value. The string itself will be
     * copied. NULL is also accepted and treated as an empty string.
     */
    void set(const char *s) {type=STR; this->s=s?s:"";}

    /**
     * Sets the value to the given string value.
     */
    void set(const std::string& s) {type=STR; this->s=s;}

    /**
     * Sets the value to the given cXMLElement.
     */
    void set(cXMLElement *x) {type=XML; xml=x;}

    /**
     * Copies the value from a cPar.
     */
    void set(const cPar& par);
    //@}

    /** @name Getter functions. Note that overloaded conversion operators also exist. */
    //@{

    /**
     * Returns value as a boolean. The type must be BOOL.
     */
    bool boolValue() const {assertType(BOOL); return bl;}

    /**
     * Returns value as long. The type must be DBL (Note: there is no LONG.)
     */
    long longValue() const {assertType(DBL); return (long)dbl;}

    /**
     * Returns value as double. The type must be DBL.
     */
    double doubleValue() const {assertType(DBL); return dbl;}

    /**
     * Returns the numeric value as a double converted to the given unit.
     * If the current unit cannot be converted to the given one, an error
     * will be thrown. The type must be DBL.
     */
    double doubleValueInUnit(const char *unit) const {return convertUnit(dbl, dblunit, unit);}

    /**
     * Returns the unit ("s", "mW", "Hz", "bps", etc), or NULL if there was no
     * unit was specified. Unit is only valid for the DBL type.
     */
    const char *getUnit() const {assertType(DBL); return dblunit;}

    /**
     * Returns value as const char *. The type must be STR.
     */
    const char *stringValue() const {assertType(STR); return s.c_str();}

    /**
     * Returns value as std::string. The type must be STR.
     */
    std::string stdstringValue() const {assertType(STR); return s;}

    /**
     * Returns value as pointer to cXMLElement. The type must be XML.
     */
    cXMLElement *xmlValue() const {assertType(XML); return xml;}
    //@}

    /** @name Overloaded assignment and conversion operators. */
    //@{

    /**
     * Equivalent to set(bool).
     */
    cNEDValue& operator=(bool b)  {set(b); return *this;}

    /**
     * Converts the argument to long, and calls set(long).
     */
    cNEDValue& operator=(char c)  {set((long)c); return *this;}

    /**
     * Converts the argument to long, and calls set(long).
     */
    cNEDValue& operator=(unsigned char c)  {set((long)c); return *this;}

    /**
     * Converts the argument to long, and calls set(long).
     */
    cNEDValue& operator=(int i)  {set((long)i); return *this;}

    /**
     * Converts the argument to long, and calls set(long).
     */
    cNEDValue& operator=(unsigned int i)  {set((long)i); return *this;}

    /**
     * Converts the argument to long, and calls set(long).
     */
    cNEDValue& operator=(short i)  {set((long)i); return *this;}

    /**
     * Converts the argument to long, and calls set(long).
     */
    cNEDValue& operator=(unsigned short i)  {set((long)i); return *this;}

    /**
     * Equivalent to set(long).
     */
    cNEDValue& operator=(long l)  {set(l); return *this;}

    /**
     * Converts the argument to long, and calls set(long).
     */
    cNEDValue& operator=(unsigned long l) {set((long)l); return *this;}

    /**
     * Equivalent to setDoubleValue().
     */
    cNEDValue& operator=(double d)  {set(d); return *this;}

    /**
     * Converts the argument to double, and calls set(double).
     */
    cNEDValue& operator=(long double d)  {set((double)d); return *this;}

    /**
     * Equivalent to set(const char *).
     */
    cNEDValue& operator=(const char *s)  {set(s); return *this;}

    /**
     * Equivalent to set(const std::string&).
     */
    cNEDValue& operator=(const std::string& s)  {set(s); return *this;}

    /**
     * Equivalent to set(cXMLElement *).
     */
    cNEDValue& operator=(cXMLElement *node)  {set(node); return *this;}

    /**
     * Equivalent to set(const cPar&).
     */
    cNEDValue& operator=(const cPar& par)  {set(par); return *this;}

    /**
     * Equivalent to boolValue().
     */
    operator bool() const  {return boolValue();}

    /**
     * Calls longValue() and converts the result to char.
     */
    operator char() const  {return (char)longValue();}

    /**
     * Calls longValue() and converts the result to unsigned char.
     */
    operator unsigned char() const  {return (unsigned char)longValue();}

    /**
     * Calls longValue() and converts the result to int.
     */
    operator int() const  {return (int)longValue();}

    /**
     * Calls longValue() and converts the result to unsigned int.
     */
    operator unsigned int() const  {return (unsigned int)longValue();}

    /**
     * Calls longValue() and converts the result to short.
     */
    operator short() const  {return (short)longValue();}

    /**
     * Calls longValue() and converts the result to unsigned short.
     */
    operator unsigned short() const  {return (unsigned short)longValue();}

    /**
     * Equivalent to longValue().
     */
    operator long() const  {return longValue();}

    /**
     * Calls longValue() and converts the result to unsigned long.
     */
    operator unsigned long() const  {return longValue();}

    /**
     * Equivalent to doubleValue().
     */
    operator double() const  {return doubleValue();}

    /**
     * Calls doubleValue() and converts the result to long double.
     */
    operator long double() const  {return doubleValue();}

    /**
     * Equivalent to stringValue().
     */
    operator const char *() const  {return stringValue();}

    /**
     * Equivalent to stdstringValue().
     */
    operator std::string() const  {return stdstringValue();}

    /**
     * Equivalent to xmlValue(). NOTE: The lifetime of the returned object tree
     * is limited; see xmlValue() for details.
     */
    operator cXMLElement *() const  {return xmlValue();}
    //@}
};

NAMESPACE_END

#endif


