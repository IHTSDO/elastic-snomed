package org.snomed.snowstorm.fhir.services;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetFilterComponent;
import org.hl7.fhir.r4.model.ValueSet.FilterOperator;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODE;

@Component
class FHIRHelper {

	private static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";

	@Autowired
	private CodeSystemService codeSystemService;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	Integer getSnomedVersion(String versionStr) {
		String versionUri = "/" + FHIRConstants.VERSION + "/";
		return !versionStr.contains("/" + FHIRConstants.VERSION + "/")
				? null
				: Integer.parseInt(versionStr.substring(versionStr.indexOf(versionUri) + versionUri.length()));
	}

	static String translateDescType(String typeSctid) {
		switch (typeSctid) {
			case Concepts.FSN : return "Fully specified name";
			case Concepts.SYNONYM : return "Synonym";
			case Concepts.TEXT_DEFINITION : return "Text definition";
		}
		return null;
	}

	String getSnomedEditionModule(StringType versionStr) {
		if (versionStr == null || versionStr.getValueAsString().isEmpty() || versionStr.getValueAsString().equals(FHIRConstants.SNOMED_URI)) {
			return Concepts.CORE_MODULE;
		}
		return getSnomedEditionModule(versionStr.getValueAsString());
	}

	private String getSnomedEditionModule(String versionStr) {
		return !versionStr.contains("/" + FHIRConstants.VERSION + "/")
				? versionStr.substring(FHIRConstants.SNOMED_URI.length() + 1,  FHIRConstants.SNOMED_URI.length() + versionStr.length() - FHIRConstants.SNOMED_URI.length())
				: versionStr.substring(FHIRConstants.SNOMED_URI.length() + 1, versionStr.indexOf("/" + FHIRConstants.VERSION + "/"));
	}

	public String getBranchPathForCodeSystemVersion(StringType codeSystemVersionUri) {
		String branchPath;
		String defaultModule = getSnomedEditionModule(codeSystemVersionUri);
		Integer editionVersionString = null;
		if (codeSystemVersionUri != null) {
			editionVersionString = getSnomedVersion(codeSystemVersionUri.toString());
		}

		org.snomed.snowstorm.core.data.domain.CodeSystem codeSystem = codeSystemService.findByDefaultModule(defaultModule);
		if (codeSystem == null) {
			String msg = String.format("No code system with default module %s.", defaultModule);
			//throw new NotFoundException());
			logger.error(msg + " Using MAIN.");
			return "MAIN";
		}

		CodeSystemVersion codeSystemVersion;
		String shortName = codeSystem.getShortName();
		if (editionVersionString != null) {
			// Lookup specific version
			codeSystemVersion = codeSystemService.findVersion(shortName, editionVersionString);
			branchPath = codeSystemVersion.getBranchPath();
		} else {
			// Lookup latest effective version (future versions will not be used until publication date)
			branchPath = codeSystem.getLatestVersion().getBranchPath();
		}
		if (branchPath == null) {
			throw new NotFoundException(String.format("No branch found for Code system %s with default module %s.", shortName, defaultModule));
		}
		return branchPath;
	}

	public List<String> getLanguageCodes(List<String> designations, HttpServletRequest request) throws FHIROperationException {
		// Use designations by default, or fall back to language headers
		if (designations != null) {
			List<String> languageCodes = new ArrayList<>();
			for (String designation : designations) {
				if (designation.length() > 5) {
					throw new FHIROperationException(IssueType.VALUE, "'designation' parameters are currently limited to language codes.  Received '" + designation + "'");
				}
				languageCodes.add(designation);
			}
			if (languageCodes.isEmpty()) {
				languageCodes.add(DEFAULT_LANGUAGE_CODE);
			}
			return languageCodes;
		} else {
			return ControllerHelper.getLanguageCodes(request.getHeader(ACCEPT_LANGUAGE_HEADER));
		}
	}

	List<LanguageDialect> getLanguageDialects(List<String> languageCodes) {
		return languageCodes.stream().map(LanguageDialect::new).collect(Collectors.toList());
	}

	public String convertToECL(ConceptSetComponent setDefn) throws FHIROperationException {
		String ecl = "";
		boolean firstItem = true;
		for (ConceptReferenceComponent concept : setDefn.getConcept()) {
			if (firstItem) {
				firstItem = false;
			} else {
				ecl += " OR ";
			}
			ecl += concept.getCode() + "|" + concept.getDisplay() + "|";
		}

		for (ConceptSetFilterComponent filter : setDefn.getFilter()) {
			if (firstItem) {
				firstItem = false;
			} else {
				ecl += " AND ";
			}
			if (filter.getProperty().equals("concept")) {
				ecl += convertOperationToECL(filter.getOp());
				ecl += filter.getValue();
			} else if (filter.getProperty().equals("constraint")) {
				if (filter.getOp().toCode() != "=") {
					throw new FHIROperationException (IssueType.NOTSUPPORTED , "ValueSet compose filter 'constaint' operation - only '=' currently implemented");
				}
				ecl += filter.getValue();
			} else {
				throw new FHIROperationException (IssueType.NOTSUPPORTED , "ValueSet compose filter property - only 'concept' and 'constraint' currently implemented");
			}

		}

		return ecl;
	}

	private String convertOperationToECL(FilterOperator op) throws FHIROperationException {
		switch (op.toCode()) {
			case "is-a" : return " << ";
			case "=" : return " ";
			case "descendant-of" : return " < ";
			default :
				throw new FHIROperationException (IssueType.NOTSUPPORTED , "ValueSet compose filter operation " + op.toCode() + " (" + op.getDisplay() + ") not currently supported");
		}
	}
}
