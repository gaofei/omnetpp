//==========================================================================
// nedsemanticvalidator.cc - part of the OMNeT++ Discrete System Simulation System
//
// Contents:
//   class NEDSemanticValidator
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 2002-2004 Andras Varga

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#include "nederror.h"
#include "nedsemanticvalidator.h"
#include "nedcompiler.h" // for NEDSymbolTable


inline bool strnotnull(const char *s)
{
    return s && s[0];
}

NEDSemanticValidator::NEDSemanticValidator(bool parsedExpr, NEDSymbolTable *symbtab)
{
    parsedExpressions = parsedExpr;
    symboltable = symbtab;
}

NEDSemanticValidator::~NEDSemanticValidator()
{
}

void NEDSemanticValidator::validateElement(NedFilesNode *node)
{
}

void NEDSemanticValidator::validateElement(NedFileNode *node)
{
}

void NEDSemanticValidator::validateElement(ImportNode *node)
{
}

void NEDSemanticValidator::validateElement(ImportedFileNode *node)
{
}

void NEDSemanticValidator::validateElement(ChannelNode *node)
{
    // make sure channel type name does not exist yet
    if (symboltable->getChannelDeclaration(node->getName()))
        NEDError(node, "redefinition of channel with name '%s'",node->getName());
}

void NEDSemanticValidator::validateElement(ChannelAttrNode *node)
{
}

void NEDSemanticValidator::validateElement(NetworkNode *node)
{
    // make sure network name does not exist yet
    if (symboltable->getNetworkDeclaration(node->getName()))
        NEDError(node, "redefinition of network with name '%s'",node->getName());

    // make sure module type exists
    const char *type_name = node->getTypeName();
    moduletypedecl = symboltable->getModuleDeclaration(type_name);
    if (!moduletypedecl)
        NEDError(node, "unknown module type '%s'",type_name);
}

void NEDSemanticValidator::validateElement(SimpleModuleNode *node)
{
    // make sure module type name does not exist yet
    if (symboltable->getModuleDeclaration(node->getName()))
        NEDError(node, "redefinition of module with name '%s'",node->getName());
}

void NEDSemanticValidator::validateElement(CompoundModuleNode *node)
{
    // make sure module type name does not exist yet
    if (symboltable->getModuleDeclaration(node->getName()))
        NEDError(node, "redefinition of module with name '%s'",node->getName());
}

void NEDSemanticValidator::validateElement(ParamsNode *node)
{
}

void NEDSemanticValidator::validateElement(ParamNode *node)
{
}

void NEDSemanticValidator::validateElement(GatesNode *node)
{
}

void NEDSemanticValidator::validateElement(GateNode *node)
{
}

void NEDSemanticValidator::validateElement(MachinesNode *node)
{
}

void NEDSemanticValidator::validateElement(MachineNode *node)
{
}

void NEDSemanticValidator::validateElement(SubmodulesNode *node)
{
}

void NEDSemanticValidator::validateElement(SubmoduleNode *node)
{
    // make sure module type exists
    const char *type_name = node->getTypeName();
    moduletypedecl = symboltable->getModuleDeclaration(type_name);
    if (!moduletypedecl)
        NEDError(node, "unknown module type '%s'",type_name);
}

void NEDSemanticValidator::validateElement(SubstparamsNode *node)
{
}

void NEDSemanticValidator::validateElement(SubstparamNode *node)
{
    if (!moduletypedecl)
        return;

    // make sure parameter exists in module type
    const char *paramname = node->getName();
    ParamsNode *paramsdecl = (ParamsNode *)moduletypedecl->getFirstChildWithTag(NED_PARAMS);
    if (!paramsdecl)
    {
        NEDError(node, "module type does not have parameters");
        return;
    }
    ParamNode *paramdecl = (ParamNode *)paramsdecl->getFirstChildWithAttribute(NED_PARAM, "name", paramname);
    if (!paramdecl)
    {
        NEDError(node, "module type does not have a parameter named '%s'",paramname);
        return;
    }

    // check type matches
    //FIXME
}

void NEDSemanticValidator::validateElement(GatesizesNode *node)
{
}

void NEDSemanticValidator::validateElement(GatesizeNode *node)
{
    if (!moduletypedecl)
        return;

    // make sure gate exists in module type
    const char *gatename = node->getName();
    GatesNode *gatesdecl = (GatesNode *)moduletypedecl->getFirstChildWithTag(NED_GATES);
    if (!gatesdecl)
    {
        NEDError(node, "module type does not have gates");
        return;
    }
    GateNode *gatedecl = (GateNode *)gatesdecl->getFirstChildWithAttribute(NED_GATE, "name", gatename);
    if (!gatedecl)
    {
        NEDError(node, "module type does not have a gate named '%s'",gatename);
        return;
    }

    // check it is vector
    if (!gatedecl->getIsVector())
        NEDError(node, "gate '%s' is not a vector gate",gatename);
}

void NEDSemanticValidator::validateElement(SubstmachinesNode *node)
{
    if (!moduletypedecl)
        return;

    // make sure machine counts match in module type and here
    MachinesNode *machinesdecl = (MachinesNode *)moduletypedecl->getFirstChildWithTag(NED_MACHINES);
    NEDElement *child;

    int substcount = 0;
    for (child=node->getFirstChildWithTag(NED_SUBSTMACHINE); child; child = child->getNextSiblingWithTag(NED_SUBSTMACHINE))
        substcount++;

    int count = 0;
    if (!machinesdecl)
        count = 1;
    else
        for (child=machinesdecl->getFirstChildWithTag(NED_MACHINE); child; child = child->getNextSiblingWithTag(NED_MACHINE))
            count++;

    if (count<substcount)
        NEDError(node, "too many machines, module type expects only %d",count);
    if (count>substcount)
        NEDError(node, "too few machines, module type expects %d",count);
}

void NEDSemanticValidator::validateElement(SubstmachineNode *node)
{
}

void NEDSemanticValidator::validateElement(ConnectionsNode *node)
{
}

void NEDSemanticValidator::checkGate(GateNode *gate, bool hasGateIndex, bool isInput, NEDElement *conn)
{
    // check gate direction, check if vector
    if (hasGateIndex && !gate->isVector())
        NEDError(conn, "gate '%s' is not a vector gate", gate->getName());
    else if (!hasGateIndex && gate->isVector())
        NEDError(conn, "gate '%s' is a vector gate", gate->getName()); //FIXME also submod name in error message! or maybe "source" or "destination"?

    // check gate direction, check if vector
    if (isInput && gate->getDirection()==NED_GATEDIR_OUTPUT)
        NEDError(conn, "gate '%s' is an output gate", gate->getName());
    else if (!isInput && gate->getDirection()==NED_GATEDIR_INPUT)
        NEDError(conn, "gate '%s' is an input gate", gate->getName());
}

void NEDSemanticValidator::validateConnGate(const char *submodName, bool hasSubmodIndex,
                                            const char *gateName, bool hasGateIndex,
                                            NEDElement *parent, NEDElement *conn, bool isSrc)
{
    if (!submodName)
    {
        // connected to parent module: check such gate is declared
        NEDElement *gates = parent->getFirstChildWithTag(NED_GATES);
        if (!gates || (gate=gates->getFirstChildWithAttribute(NED_GATE, "name", gateName))==NULL)
            NEDError(conn, "compound module has no gate named '%s'", gateName);
        else
            checkGate(gate, hasGateIndex, isSrc, conn);
    }
    else
    {
        // check such submodule is declared
        NEDElement *submods = parent->getFirstChildWithTag(NED_SUBMODULES);
        SubmoduleNode *submod = NULL;
        if (!submods || (submod=(SubmoduleNode*)submods->getFirstChildWithAttribute(NED_SUBMODULE, "name", submodName))==NULL)
            NEDError(conn, "no submodule named '%s'", submodName);
        bool isSubmodVector = ...;
        if (hasSubmodIndex && !isSubmodVector)
            NEDError(conn, "submodule '%s' is not a vector submodule", submodName);
        else if (!hasSubmodIndex && isSubmodVector)
            NEDError(conn, "submodule '%s' is a vector submodule", submodName);

        // check gate
        NEDElement *submodType = symboltable->findModuleType(submod->getTypeName());
        if (!submodType)
            return; // we gave error earlier if submod type is not present
        NEDElement *gates = submodType->getFirstChildWithTag(NED_GATES);
        if (!gates || (gate=gates->getFirstChildWithAttribute(NED_GATE, "name", gateName))==NULL)
            NEDError(conn, "submodule '%s' has no gate named '%s'", submodName, gateName);
        else
            checkGate(gate, hasGateIndex, isSrc, conn);
    }
}

void NEDSemanticValidator::validateElement(ConnectionNode *node)
{
    // make sure submodule and gate names are valid, gate direction is OK
    // and that gates & modules are really vector (or really not)
    NEDElement *parent = node;
    while (parent && parent->getTagCode()!=NED_COMPOUND_MODULE)
        parent = parent->getParent();
    if (!parent)
        INTERNAL_ERROR0(node,"occurs outside a compound-module");

    bool srcModIx =   node->getFirstChildWithAttribute(NED_EXPRESSION, "target", "src-module-index")!=NULL;
    bool srcGateIx =  node->getFirstChildWithAttribute(NED_EXPRESSION, "target", "src-gate-index")!=NULL;
    bool destModIx =  node->getFirstChildWithAttribute(NED_EXPRESSION, "target", "dest-module-index")!=NULL;
    bool destGateIx = node->getFirstChildWithAttribute(NED_EXPRESSION, "target", "dest-gate-index")!=NULL;
    validateConnGate(node->getSrcModule(), srcModIx, node->getSrcGate(), srcGateIx, parent, node, true);
    validateConnGate(node->getDestModule(), destModIx, node->getDestGate(), destGateIx, parent, node, false);
}

void NEDSemanticValidator::validateElement(ConnAttrNode *node)
{
}

void NEDSemanticValidator::validateElement(ForLoopNode *node)
{
}

void NEDSemanticValidator::validateElement(LoopVarNode *node)
{
}

void NEDSemanticValidator::validateElement(DisplayStringNode *node)
{
}

void NEDSemanticValidator::validateElement(ExpressionNode *node)
{
}

void NEDSemanticValidator::validateElement(OperatorNode *node)
{
}

void NEDSemanticValidator::validateElement(FunctionNode *node)
{
}

void NEDSemanticValidator::validateElement(ParamRefNode *node)
{
}

void NEDSemanticValidator::validateElement(IdentNode *node)
{
}

void NEDSemanticValidator::validateElement(ConstNode *node)
{
}

void NEDSemanticValidator::validateElement(CplusplusNode *node)
{
}

void NEDSemanticValidator::validateElement(StructDeclNode *node)
{
}

void NEDSemanticValidator::validateElement(ClassDeclNode *node)
{
}

void NEDSemanticValidator::validateElement(EnumDeclNode *node)
{
}

void NEDSemanticValidator::validateElement(EnumNode *node)
{
    // FIXME check extends-name
}

void NEDSemanticValidator::validateElement(EnumFieldsNode *node)
{
}

void NEDSemanticValidator::validateElement(EnumFieldNode *node)
{
}

void NEDSemanticValidator::validateElement(MessageNode *node)
{
    // FIXME check extends-name
}

void NEDSemanticValidator::validateElement(ClassNode *node)
{
    // FIXME check extends-name
}

void NEDSemanticValidator::validateElement(StructNode *node)
{
    // FIXME check extends-name
}

void NEDSemanticValidator::validateElement(FieldsNode *node)
{
}

void NEDSemanticValidator::validateElement(FieldNode *node)
{
}

void NEDSemanticValidator::validateElement(PropertiesNode *node)
{
}

void NEDSemanticValidator::validateElement(PropertyNode *node)
{
}

void NEDSemanticValidator::validateElement(UnknownNode *node)
{
}

