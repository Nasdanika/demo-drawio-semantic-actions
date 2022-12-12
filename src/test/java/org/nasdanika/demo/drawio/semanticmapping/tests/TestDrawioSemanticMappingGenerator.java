package org.nasdanika.demo.drawio.semanticmapping.tests;

import org.junit.jupiter.api.Test;
import org.nasdanika.demo.drawio.semanticmapping.DrawioSemanticMappingGenerator;

public class TestDrawioSemanticMappingGenerator {
	
	@Test
	public void generate() throws Exception {
		new DrawioSemanticMappingGenerator().generate();
	}

}
