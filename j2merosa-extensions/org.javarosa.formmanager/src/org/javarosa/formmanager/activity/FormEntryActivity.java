/*
 * Copyright (C) 2009 JavaRosa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.javarosa.formmanager.activity;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

import org.javarosa.core.Context;
import org.javarosa.core.JavaRosaServiceProvider;
import org.javarosa.core.api.Constants;
import org.javarosa.core.api.IActivity;
import org.javarosa.core.api.ICommand;
import org.javarosa.core.api.IShell;
import org.javarosa.core.api.IView;
import org.javarosa.core.data.IDataPointer;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.MultiPointerAnswerData;
import org.javarosa.core.model.data.PointerAnswerData;
import org.javarosa.core.model.utils.IModelProcessor;
import org.javarosa.formmanager.controller.FormEntryController;
import org.javarosa.formmanager.controller.IControllerHost;
import org.javarosa.formmanager.model.FormEntryModel;
import org.javarosa.formmanager.properties.FormManagerProperties;
import org.javarosa.formmanager.utility.IFormDefRetrievalMethod;
import org.javarosa.formmanager.utility.ILoadHost;
import org.javarosa.formmanager.view.FormElementBinding;
import org.javarosa.formmanager.view.IFormEntryView;
import org.javarosa.formmanager.view.IFormEntryViewFactory;
 
public class FormEntryActivity implements IActivity, IControllerHost, CommandListener, ILoadHost {

	
	/** Alert if the form cannot load **/
	private Alert alert;

	/** View for entering data **/
	private IFormEntryView view;

	/** View's controller **/
	private FormEntryController controller;

	/** The form that is to be displayed to the user, and its values **/
	private FormEntryModel model;

	/** Current running context **/
	private FormEntryContext context;

	/** The parent shell **/
	private IShell parent;

	private IFormEntryViewFactory viewFactory;

	private FormLoadActivity formLoader = null;
	
	private int instanceID = -1;
	
	private IModelProcessor processor;

	/** Loading error string **/
	private final static String LOAD_ERROR = "Deepest Apologies. The form could not be loaded.";

	public FormEntryActivity(IShell parent, IFormEntryViewFactory viewFactory) {
		this.parent = parent;
		this.viewFactory = viewFactory;
		
		this.formLoader = new FormLoadActivity(this);
	}

	public void contextChanged(Context context) {
		Vector contextChanges = this.context.mergeInContext(context);

		Enumeration en = contextChanges.elements();
		while(en.hasMoreElements()) {
			String changedValue = (String)en.nextElement();
			if(changedValue == Constants.USER_KEY) {
				//Do we need to update the username?
			}
		}
	}

	public void returnFromLoading(Context context) {
		// get the form
		FormDef theForm = (FormDef)context.getElement("theForm");
		context = null;
	
		if( theForm != null){
			model = new FormEntryModel(theForm, this.instanceID, this.context.getFirstQuestionIndex());
			controller = new FormEntryController(model, this);
			model.setReadOnly(this.context.getReadOnly());
			String viewString = JavaRosaServiceProvider.instance().getPropertyManager().getSingularProperty(FormManagerProperties.VIEW_TYPE_PROPERTY);
			System.out.println("making view! " + viewString + model + controller);
			view = viewFactory.getFormEntryView(viewString, model, controller);
			System.out.println("setting context");
			view.setContext(this.context);
			controller.setView(view);
	
			System.out.println("showing view!");
			view.show();
		} else {
			displayError(LOAD_ERROR);
		}
	}
	
	private IAnswerData getAnswerData(String type, Object value) 
	{
		if(Constants.RETURN_ARG_TYPE_DATA_POINTER_LIST.equals(type)) 
		{
			IDataPointer[] answers = (IDataPointer[]) value;
			IAnswerData toReturn = new MultiPointerAnswerData(answers);
			return toReturn;
		} 
		else if(Constants.RETURN_ARG_TYPE_DATA_POINTER.equals(type))
		{
			IDataPointer answer = (IDataPointer)value;
			IAnswerData toReturn = new PointerAnswerData(answer);
			return toReturn;
		}
		else 
		{
			throw new RuntimeException("Unable to build answer data for return type: " + type);
		}
	}
	
	public void start (Context context) {
		this.instanceID = -1;
		
		if (context instanceof FormEntryContext) {
			this.context = (FormEntryContext) context;
			this.instanceID = this.context.getInstanceID();
			this.processor = this.context.getModelProcessor();
		}
		
		// Start loading the form
		this.formLoader.start(context);
	}

	public void halt () {
		//need to do anything?
		System.out.println("whoa, nelly! we're halting!");
	}

	public void resume (Context globalContext) {
		// this is a hacky non-generic solution to the "pass data back to the form" problem,
		// but can be readily modified
		view.show();
		Object returnArg =  globalContext.getElement(Constants.RETURN_ARG_KEY);
		if (globalContext.getElement(Constants.RETURN_ARG_KEY) != null) {
			String returnArgType = (String) globalContext.getElement(Constants.RETURN_ARG_TYPE_KEY);
			IAnswerData dataBack = getAnswerData(returnArgType, returnArg);
			
			controller.questionAnswered(new FormElementBinding(null, model.getQuestionIndex(), model.getForm()), dataBack);
		}
	}

	public void destroy () {

	}

	public void setView(IView v) {
		parent.setDisplay(this, v);
	}

	public void controllerReturn (String status) {
		if ("exit".equals(status)) {
			Hashtable returnArgs = new Hashtable();

			returnArgs.put("INSTANCE_ID", new Integer(model.getInstanceID()));
			returnArgs.put("DATA_MODEL", model.getForm().getDataModel());
			returnArgs.put("FORM_COMPLETE", new Boolean(model.isFormComplete()));
			returnArgs.put("QUIT_WITHOUT_SAVING", new Boolean(!model.isSaved()));
			returnArgs.put(FormEntryContext.FORM_ID, new Integer(model.getForm().getRecordId()));

			if(processor != null && model.isFormComplete() && model.isSaved()) {
				processor.initializeContext(this.context);
				processor.processModel(model.getForm().getDataModel());
				processor.loadProcessedContext(this.context);
			}
			
			parent.returnFromActivity(this, Constants.ACTIVITY_COMPLETE, returnArgs);
		} else if (Constants.ACTIVITY_TYPE_GET_IMAGES.equals(status)) {
			Hashtable returnArgs = new Hashtable();

			returnArgs.put("FORM_COMPLETE", new Boolean(false));
			returnArgs.put(Constants.ACTIVITY_LAUNCH_KEY, Constants.ACTIVITY_TYPE_GET_IMAGES);
			
			parent.returnFromActivity(this, Constants.ACTIVITY_NEEDS_RESOLUTION,
					returnArgs);
			//parent.returnFromActivity(this, Constants.ACTIVITY_COMPLETE, returnArgs);
		}
		else if (Constants.ACTIVITY_TYPE_GET_AUDIO.equals(status))
		{
			Hashtable returnArgs = new Hashtable();

			returnArgs.put("FORM_COMPLETE", new Boolean(false));
			returnArgs.put(Constants.ACTIVITY_LAUNCH_KEY, Constants.ACTIVITY_TYPE_GET_AUDIO);
			
			parent.returnFromActivity(this, Constants.ACTIVITY_NEEDS_RESOLUTION,
					returnArgs);
		}
	}

	public void commandAction(Command command, Displayable display) {
		if(display == alert) {
			parent.returnFromActivity(this, Constants.ACTIVITY_COMPLETE, null);
		}
	}

	private void displayError(String errorMsg) {
		alert = new Alert("Form Entry Error",errorMsg,null,AlertType.ERROR);
		alert.setTimeout(Alert.FOREVER);
		//setView(alert);
		//For some reason that I really can't figure out, this alert won't display the error text
		alert.setCommandListener(this);
	}
	public Context getActivityContext() {
		return context;
	}
	
	public void setRetrievalMethod(IFormDefRetrievalMethod method) {
		this.formLoader.setRetrievalMethod(method);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.javarosa.core.api.IActivity#setShell(org.javarosa.core.api.IShell)
	 */
	public void setShell(IShell shell) {
		this.parent = shell;
	}

	public void annotateCommand(ICommand command) {
		// TODO Auto-generated method stub
		
	}
}