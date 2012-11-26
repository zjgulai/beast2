package beast.app.beauti;



import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import beast.app.draw.InputEditor;
import beast.app.draw.IntegerInputEditor;
import beast.app.draw.ParameterInputEditor;
import beast.app.draw.PluginInputEditor;
import beast.app.draw.SmallLabel;
import beast.core.Distribution;
import beast.core.Input;
import beast.core.MCMC;
import beast.core.Operator;
import beast.core.Plugin;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.core.util.CompoundDistribution;
import beast.evolution.alignment.Alignment;
import beast.evolution.likelihood.GenericTreeLikelihood;
import beast.evolution.operators.DeltaExchangeOperator;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.sitemodel.SiteModelInterface;

public class SiteModelInputEditor extends PluginInputEditor {
    private static final long serialVersionUID = 1L;

    IntegerInputEditor categoryCountEditor;
    JTextField categoryCountEntry;
    InputEditor gammaShapeEditor;
    ParameterInputEditor inVarEditor;

    // vars for dealing with mean-rate delta exchange operator
    JCheckBox fixMeanRatesCheckBox;
    DeltaExchangeOperator operator;
    protected SmallLabel fixMeanRatesValidateLabel;

	public SiteModelInputEditor(BeautiDoc doc) {
		super(doc);
	}

    @Override
    public Class<?> type() {
        return SiteModelInterface.class;
    }
    
    @Override
    public void init(Input<?> input, Plugin plugin, int itemNr,
    		ExpandOption bExpandOption, boolean bAddButtons) {
    	fixMeanRatesCheckBox = new JCheckBox("Fix mean mutation rate");
    	fixMeanRatesCheckBox.setName("FixMeanMutationRate");
    	super.init(input, plugin, itemNr, bExpandOption, bAddButtons);
    	
		List<Operator> operators = ((MCMC) doc.mcmc.get()).operatorsInput.get();
    	fixMeanRatesCheckBox.addActionListener(new ActionListener() {			
			@Override
			public void actionPerformed(ActionEvent e) {
				JCheckBox averageRatesBox = (JCheckBox) e.getSource();
				boolean averageRates = averageRatesBox.isSelected();				
				List<Operator> operators = ((MCMC) doc.mcmc.get()).operatorsInput.get();
				if (averageRates) {
					// connect DeltaExchangeOperator
					if (!operators.contains(operator)) {
						operators.add(operator);
					}
					// set up relative weights
					setUpOperator();
				} else {
					operators.remove(operator);
					fixMeanRatesValidateLabel.setVisible(false);
					repaint();
				}
			}

		});
    	operator = (DeltaExchangeOperator) doc.pluginmap.get("FixMeanMutationRatesOperator");
    	if (operator == null) {
    		operator = new DeltaExchangeOperator();
    		try {
    			operator.setID("FixMeanMutationRatesOperator");
				operator.initByName("weight", 2.0, "delta", 0.75);
			} catch (Exception e1) {
				// ignore initAndValidate exception
			}
    		doc.addPlugin(operator);
    	}
		fixMeanRatesCheckBox.setSelected(operators.contains(operator));
		Box box = Box.createHorizontalBox();
		box.add(fixMeanRatesCheckBox);
		box.add(Box.createHorizontalGlue());
		fixMeanRatesValidateLabel = new SmallLabel("x", Color.GREEN);
		fixMeanRatesValidateLabel.setVisible(false);
		box.add(fixMeanRatesValidateLabel);
		
    	if (doc.alignments.size() > 1 && operator != null) {
        	JComponent component = (JComponent) getComponents()[0];
    		component.add(box);
    	}
		setUpOperator();
    }
    
//	@Override
//    public Class<?> [] types() {
//		Class<?>[] types = {SiteModel.class, SiteModel.Base.class}; 
//		return types;
//    }


    public InputEditor createGammaCategoryCountEditor() throws Exception {
    	SiteModel sitemodel = ((SiteModel) m_input.get()); 
        Input<?> input = sitemodel.gammaCategoryCount;
        categoryCountEditor = new IntegerInputEditor(doc) {
			private static final long serialVersionUID = 1L;

			public void validateInput() {
        		super.validateInput();
            	SiteModel sitemodel = (SiteModel) m_plugin; 
                if (sitemodel.gammaCategoryCount.get() < 2 && ((RealParameter)sitemodel.shapeParameterInput.get()).m_bIsEstimated.get()) {
                	m_validateLabel.m_circleColor = Color.orange;
                	m_validateLabel.setToolTipText("shape parameter is estimated, but not used");
                	m_validateLabel.setVisible(true);
                }
        	};
        };
        
        categoryCountEditor.init(input, sitemodel, -1, ExpandOption.FALSE, true);
        categoryCountEntry = categoryCountEditor.getEntry();
        categoryCountEntry.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                processEntry2();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                processEntry2();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                processEntry2();
            }
        });
        
       	categoryCountEditor.validateInput();
        return categoryCountEditor;
    }

    void processEntry2() {
        String sCategories = categoryCountEntry.getText();
        try {
            int nCategories = Integer.parseInt(sCategories);
            gammaShapeEditor.getComponent().setVisible(nCategories >= 2);
            repaint();
        } catch (java.lang.NumberFormatException e) {
            // ignore.
        }
    }

    public InputEditor createShapeEditor() throws Exception {
        Input<?> input = ((SiteModel) m_input.get()).shapeParameterInput;
        gammaShapeEditor = doc.getInpuEditorFactory().createInputEditor(input, (Plugin) m_input.get(), doc);
        gammaShapeEditor.getComponent().setVisible(((SiteModel) m_input.get()).gammaCategoryCount.get() >= 2);
        return gammaShapeEditor;
    }

    public InputEditor createProportionInvariantEditor() throws Exception {
        Input<?> input = ((SiteModel) m_input.get()).invarParameterInput;
        inVarEditor = new ParameterInputEditor(doc) {
			private static final long serialVersionUID = 1L;

			@Override
            public void validateInput() {
				RealParameter p = (RealParameter) m_input.get();
				if (p.m_bIsEstimated.get() && Double.parseDouble(p.m_pValues.get()) <= 0.0) {
                    m_validateLabel.setVisible(true);
                    m_validateLabel.setToolTipText("<html><p>Proportion invariant should be non-zero when estimating</p></html>");
                    return;
				}
				if (Double.parseDouble(p.m_pValues.get()) < 0.0 || Double.parseDouble(p.m_pValues.get()) >= 1.0) {
                    m_validateLabel.setVisible(true);
                    m_validateLabel.setToolTipText("<html><p>Proportion invariant should be from 0 to 1 (exclusive 1)</p></html>");
                    return;
				}
            	super.validateInput();
            }
        };
        inVarEditor.init(input, (Plugin) m_input.get(), -1, ExpandOption.FALSE, true);
        inVarEditor.addValidationListener(this);
        return inVarEditor;
    }


    /** set up relative weights and parameter input **/
    private void setUpOperator() {
    	String weights = "";
    	List<RealParameter> parameters = operator.parameterInput.get();
    	parameters.clear();
    	double commonClockRate = -1;
    	boolean bAllClocksAreEqual = true;
		try {
			CompoundDistribution likelihood = (CompoundDistribution) doc.pluginmap.get("likelihood");
			for (Distribution d : likelihood.pDistributions.get()) {
				GenericTreeLikelihood treelikelihood = (GenericTreeLikelihood) d;
	    		Alignment data = treelikelihood.m_data.get(); 
	    		int weight = data.getSiteCount();
	    		if (treelikelihood.m_pSiteModel.get() instanceof SiteModel) {
		    		SiteModel siteModel = (SiteModel) treelikelihood.m_pSiteModel.get();
		    		RealParameter mutationRate = siteModel.muParameterInput.get();
		    		//clockRate.m_bIsEstimated.setValue(true, clockRate);
		    		if (mutationRate.m_bIsEstimated.get()) {
		    			if (commonClockRate < 0) {
		    				commonClockRate = Double.parseDouble(mutationRate.m_pValues.get());
		    			} else {
		    				if (Math.abs(commonClockRate - Double.parseDouble(mutationRate.m_pValues.get())) > 1e-10) {
		    					bAllClocksAreEqual = false;
		    				}
		    			}
	    				weights += weight + " ";
		    			parameters.add(mutationRate);
		    		}
	    		}
	    	}
	    	if (!fixMeanRatesCheckBox.isSelected()) {
	    		fixMeanRatesValidateLabel.setVisible(false);
				repaint();
	    		return;
	    	}
	    	if (parameters.size() == 0) {
	    		fixMeanRatesValidateLabel.setVisible(true);
	    		fixMeanRatesValidateLabel.m_circleColor = Color.red;
	    		fixMeanRatesValidateLabel.setToolTipText("The model is invalid: At least one clock rate should be estimated.");
				repaint();
	    		return;
	    	}

	    	IntegerParameter weightParameter = new IntegerParameter(weights);
			weightParameter.setID("weightparameter");
			weightParameter.m_bIsEstimated.setValue(false, weightParameter);
	    	operator.input_parameterWeights.setValue(weightParameter, operator);
	    	if (!bAllClocksAreEqual) {
	    		fixMeanRatesValidateLabel.setVisible(true);
	    		fixMeanRatesValidateLabel.m_circleColor = Color.orange;
	    		fixMeanRatesValidateLabel.setToolTipText("Not all clocks are equal. Are you sure this is what you want?");
	    	} else if (parameters.size() == 1) {
	    		fixMeanRatesValidateLabel.setVisible(true);
	    		fixMeanRatesValidateLabel.m_circleColor = Color.orange;
	    		fixMeanRatesValidateLabel.setToolTipText("At least 2 clock models should have their rate estimated");
	    	} else if (parameters.size() < doc.alignments.size()) {
	    		fixMeanRatesValidateLabel.setVisible(true);
	    		fixMeanRatesValidateLabel.m_circleColor = Color.orange;
	    		fixMeanRatesValidateLabel.setToolTipText("Not all partitions have their rate estimated");
	    	} else {
	    		fixMeanRatesValidateLabel.setVisible(false);
	    	}
			repaint();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
