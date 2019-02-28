package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@Api(tags = "MRCM XML", description = "-")
@RequestMapping(produces = "application/json")
public class MRCMController {

	@Autowired
	private MRCMService mrcmService;

	@ApiOperation("Retrieve MRCM domain attributes applicable for the given parents.")
	@RequestMapping(value = "/mrcm/{path}/domain-attributes", method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<ConceptMini> retrieveDomainAttributes(
			@PathVariable String path,
			@RequestParam(required = false) Set<Long> parentIds,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		String branchPath = BranchPathUriUtil.decodePath(path);
		List<String> languageCodes = ControllerHelper.getLanguageCodes(acceptLanguageHeader);
		return new ItemsPage<>(mrcmService.retrieveDomainAttributes(branchPath, parentIds, languageCodes));
	}

	@ApiOperation("Retrieve valid values for the given attribute and term prefix.")
	@RequestMapping(value = "/mrcm/{path}/attribute-values/{attributeId}", method = RequestMethod.GET)
	@ResponseBody
	public ItemsPage<ConceptMini> retrieveAttributeValues(
			@PathVariable String path,
			@PathVariable String attributeId,
			@RequestParam String termPrefix,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		String branchPath = BranchPathUriUtil.decodePath(path);
		List<String> languageCodes = ControllerHelper.getLanguageCodes(acceptLanguageHeader);
		return new ItemsPage<>(mrcmService.retrieveAttributeValues(branchPath, attributeId, termPrefix, languageCodes));
	}

	@ApiOperation("Reload MRCM from XML files. This is an alternative implementation to the reference sets.")
	@RequestMapping(value = "/mrcm/reload", method = RequestMethod.POST)
	public void reloadMrcm() throws ServiceException {
		mrcmService.loadFromFiles();
	}

}
