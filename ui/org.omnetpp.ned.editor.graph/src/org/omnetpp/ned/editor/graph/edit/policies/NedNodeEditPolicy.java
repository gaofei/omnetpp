package org.omnetpp.ned.editor.graph.edit.policies;

import org.eclipse.draw2d.Connection;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.Request;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.UnexecutableCommand;
import org.eclipse.gef.editpolicies.GraphicalNodeEditPolicy;
import org.eclipse.gef.requests.CreateConnectionRequest;
import org.eclipse.gef.requests.DropRequest;
import org.eclipse.gef.requests.ReconnectRequest;
import org.omnetpp.ned.editor.graph.edit.ModuleEditPart;
import org.omnetpp.ned.editor.graph.figures.ConnectionFigure;
import org.omnetpp.ned.editor.graph.figures.GateAnchor;
import org.omnetpp.ned.editor.graph.figures.ModuleFigure;
import org.omnetpp.ned.editor.graph.model.commands.ConnectionCommand;
import org.omnetpp.ned2.model.ConnectionNodeEx;
import org.omnetpp.ned2.model.INamedGraphNode;

public class NedNodeEditPolicy extends GraphicalNodeEditPolicy {
	
	// Used to give feedback during dragging
	@Override
	protected Connection createDummyConnection(Request req) {
		ConnectionFigure cf = new ConnectionFigure();
		cf.setArrowEnabled(true);
		return cf;
	}

	// called during connection creation on the first click
	@Override
    protected Command getConnectionCreateCommand(CreateConnectionRequest request) {
        ConnectionNodeEx conn = (ConnectionNodeEx)request.getNewObject();
        ConnectionCommand command = new ConnectionCommand(conn);
        command.setSrcModule(getGraphNodeModel());
        GateAnchor anchor = (GateAnchor)getModuleEditPart().getSourceConnectionAnchor(request);
        if (anchor == null) 
        	return UnexecutableCommand.INSTANCE;
        
//        command.setSrcGate(anchor.getGateName());
        request.setStartCommand(command);
        return command;
    }

    // called when clicked on the second node durnig connection creation
    @Override
    protected Command getConnectionCompleteCommand(CreateConnectionRequest request) {
        ConnectionCommand command = (ConnectionCommand) request.getStartCommand();
        command.setDestModule(getGraphNodeModel());
        GateAnchor anchor = (GateAnchor)getModuleEditPart().getTargetConnectionAnchor(request);
        if (anchor == null) 
        	return UnexecutableCommand.INSTANCE;
        
//        command.setDestGate(anchor.getGateName());
        
        return command;
    }

    @Override
    protected Command getReconnectTargetCommand(ReconnectRequest request) {
    	ConnectionNodeEx conn = (ConnectionNodeEx) request.getConnectionEditPart().getModel();
        ConnectionCommand cmd = new ConnectionCommand(conn);

        GateAnchor anchor = (GateAnchor)getModuleEditPart().getTargetConnectionAnchor(request);
        if (anchor == null) 
        	return UnexecutableCommand.INSTANCE;
        cmd.setDestModule(getGraphNodeModel());
//        cmd.setDestGate(anchor.getGateName());
        return cmd;
    }

    @Override
    protected Command getReconnectSourceCommand(ReconnectRequest request) {
        ConnectionCommand cmd = new ConnectionCommand((ConnectionNodeEx) request.getConnectionEditPart().getModel());

        GateAnchor anchor = (GateAnchor)getModuleEditPart().getSourceConnectionAnchor(request);
        if (anchor == null) 
        	return UnexecutableCommand.INSTANCE;
        cmd.setSrcModule(getGraphNodeModel());
  //      cmd.setSrcGate(anchor.getGateName());
        return cmd;
    }
    
    /**
     * Feedback should be added to the scaled feedback layer.
     * 
     * @see org.eclipse.gef.editpolicies.GraphicalEditPolicy#getFeedbackLayer()
     */
    @Override
    protected IFigure getFeedbackLayer() {
        return getLayer(LayerConstants.SCALED_FEEDBACK_LAYER);
    }

    protected ModuleEditPart getModuleEditPart() {
        return (ModuleEditPart) getHost();
    }

    protected INamedGraphNode getGraphNodeModel() {
        return (INamedGraphNode) getHost().getModel();
    }

    protected ModuleFigure getModuleFigure() {
        return (ModuleFigure)getHostFigure();
    }

}