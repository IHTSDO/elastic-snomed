package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.snowstorm.ecl.ConceptSelectorHelper;
import org.snomed.snowstorm.ecl.ECLContentService;
import org.snomed.snowstorm.ecl.deserializer.ECLModelDeserializer;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.refinement.SEclRefinement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static com.google.common.collect.Sets.newHashSet;

public class SRefinedExpressionConstraint extends RefinedExpressionConstraint implements SExpressionConstraint {

	@SuppressWarnings("unused")
	// For JSON
	private SRefinedExpressionConstraint() {
		this(null, null);
	}

	public SRefinedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, EclRefinement eclRefinement) {
		super(subExpressionConstraint, eclRefinement);
	}

	@Override
	public Optional<Page<Long>> select(String path, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter,
			PageRequest pageRequest, ECLContentService eclContentService) {

		return ConceptSelectorHelper.select(this, path, branchCriteria, stated, conceptIdFilter, pageRequest, eclContentService);
	}

	@Override
	public Optional<Page<Long>> select(RefinementBuilder refinementBuilder) {
		return ConceptSelectorHelper.select(this, refinementBuilder);
	}

	@Override
	public Set<String> getConceptIds() {
		Set<String> conceptIds = newHashSet();
		conceptIds.addAll(((SSubExpressionConstraint) subexpressionConstraint).getConceptIds());
		conceptIds.addAll(((SEclRefinement) eclRefinement).getConceptIds());
		return conceptIds;
	}

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		((SSubExpressionConstraint)subexpressionConstraint).addCriteria(refinementBuilder);
		((SEclRefinement)eclRefinement).addCriteria(refinementBuilder);

		if (refinementBuilder.isInclusionFilterRequired()) {
			refinementBuilder.setInclusionFilter(queryConcept -> {
				Map<Integer, Map<String, List<Object>>> conceptAttributes = queryConcept.getGroupedAttributesMap();
				MatchContext matchContext = new MatchContext(conceptAttributes);
				return ((SEclRefinement) eclRefinement).isMatch(matchContext);
			});
		}
	}

	@Override
	public String toEclString() {
		return toString(new StringBuffer()).toString();
	}

	public StringBuffer toString(StringBuffer buffer) {
		ECLModelDeserializer.expressionConstraintToString(subexpressionConstraint, buffer);
		buffer.append(" : ");
		ECLModelDeserializer.expressionConstraintToString((SEclRefinement) eclRefinement, buffer);
		return buffer;
	}
}
