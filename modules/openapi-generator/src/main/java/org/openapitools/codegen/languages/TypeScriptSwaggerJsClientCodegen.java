/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import java.io.File;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.DocumentationFeature;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.openapitools.codegen.utils.StringUtils.camelize;

public class TypeScriptSwaggerJsClientCodegen extends AbstractTypeScriptClientCodegen {

	private static final String GENERATOR_ID = "typescript-swagger-js-typings";

	private static final String NPM_REPOSITORY = "npmRepository";

	private static final String DEFAULT_IMPORT_PREFIX = "./";

	protected String npmRepository = null;

	public TypeScriptSwaggerJsClientCodegen() {
		super();
		this.cliOptions.add(new CliOption(NPM_REPOSITORY, "Use this property to set an url your private npmRepo in the package.json"));
		this.outputFolder = "generated-code" + File.separator + GENERATOR_ID;
		this.embeddedTemplateDir = this.templateDir = GENERATOR_ID;

		modifyFeatureSet(features -> features.includeDocumentationFeatures(DocumentationFeature.Readme));

		supportsMultipleInheritance = true;
		supportsInheritance = true;

		modelTemplateFiles.put("model.mustache", ".d.ts");
		apiTemplateFiles.put("api.interface.mustache", ".d.ts");
		languageSpecificPrimitives.add("Blob");
		typeMapping.put("file", "Blob");
		typeMapping.put("Set", "Array");
		typeMapping.put("set", "Array");
		instantiationTypes.put("set", "Array");

		apiPackage = "api";
		modelPackage = "model";

		this.cliOptions.add(new CliOption(NPM_REPOSITORY,
				"Use this property to set an url your private npmRepo in the package.json"));
	}

	@Override
	public String getName() {
		return GENERATOR_ID;
	}

	@Override
	public String getHelp() {
		return "Generates TypeScript definitions to be used with the Swagger Client library (https://github.com/swagger-api/swagger-js).";
	}

	@Override
	public void processOpts() {
		super.processOpts();

		supportingFiles.add(new SupportingFile("README.mustache", "README.md"));
		supportingFiles.add(new SupportingFile("package.mustache", "package.json"));
		supportingFiles.add(new SupportingFile("index.d.ts.mustache", "index.d.ts"));
		supportingFiles.add(new SupportingFile("index.js", "index.js"));
		supportingFiles.add(new SupportingFile("api.mustache", "api.d.ts"));
		supportingFiles.add(new SupportingFile("api.json.mustache", "api.json"));

		if (!additionalProperties.containsKey(NPM_VERSION)) {
			additionalProperties.put(NPM_VERSION, "1.0.0");
		}
	}

	@Override
	protected void addAdditionPropertiesToCodeGenModel(CodegenModel codegenModel, Schema schema) {
		codegenModel.additionalPropertiesType = getTypeDeclaration(getAdditionalProperties(schema));
		addImport(codegenModel, codegenModel.additionalPropertiesType);
	}

	@Override
	public boolean isDataTypeFile(final String dataType) {
		return "Blob".equals(dataType);
	}

	@Override
	public String getTypeDeclaration(Schema p) {
		if (ModelUtils.isFileSchema(p)) {
			return "Blob";
		} else {
			return super.getTypeDeclaration(p);
		}
	}


	private String applyLocalTypeMapping(String type) {
		if (typeMapping.containsKey(type)) {
			type = typeMapping.get(type);
		}
		return type;
	}

	@Override
	public void postProcessParameter(CodegenParameter parameter) {
		super.postProcessParameter(parameter);
		parameter.dataType = applyLocalTypeMapping(parameter.dataType);
	}

	@Override
	public boolean getUseInlineModelResolver() {
		return false;
	}

	@Override
	public OperationsMap postProcessOperationsWithModels(OperationsMap operations, List<ModelMap> allModels) {
		OperationMap objs = operations.getOperations();
		List<CodegenOperation> ops = objs.getOperation();
		for (CodegenOperation op : ops) {
			postProcessOperation(op, operations);
		}
		return operations;
	}

	private void postProcessOperation(CodegenOperation op, OperationsMap operations) {
		OperationMap objs = operations.getOperations();
		String apiClassName = objs.getClassname();
		for (Tag tag : op.tags) {
			String tagApiName = toApiName(sanitizeTag(tag.getName()));
			if (tagApiName.equals(apiClassName)) {
				operations.put("tagName", tag.getName());
				return;
			}
		}
	}

	@Override
	public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
		return new ExtendedCodegenOperation(super.fromOperation(path, httpMethod, operation, servers));
	}

	@Override
	public ModelsMap postProcessModels(ModelsMap modelsMap) {
		ModelsMap result = super.postProcessModels(modelsMap);
		modelsMap.getModels().forEach(modelMap -> {
			CodegenModel model = modelMap.getModel();
			if (model.isArray) {
				if (model.allParents == null) {
					model.allParents = new ArrayList<>();
				}
				if (!model.allParents.contains(model.parent)) {
					model.allParents.add(model.parent);
				}
			}
		});
		return postProcessModelsEnum(result);
	}

	@Override
	public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
		Map<String, ModelsMap> result = super.postProcessAllModels(objs);
		for (ModelsMap entry : result.values()) {
			for (ModelMap mo : entry.getModels()) {
				CodegenModel cm = mo.getModel();
				// Add additional filename information for imports
				Set<String> parsedImports = parseImports(cm);
				mo.put("tsImports", toTsImports(cm, parsedImports));
			}
		}
		return result;
	}

	/**
	 * Parse imports
	 */
	private Set<String> parseImports(CodegenModel cm) {
		Set<String> newImports = new HashSet<>();
		if (cm.imports.size() > 0) {
			for (String name : cm.imports) {
				if (name.contains(" | ")) {
					String[] parts = name.split(" \\| ");
					Collections.addAll(newImports, parts);
				} else {
					newImports.add(name);
				}
			}
		}
		return newImports;
	}

	private List<Map<String, String>> toTsImports(CodegenModel cm, Set<String> imports) {
		List<Map<String, String>> tsImports = new ArrayList<>();
		for (String im : imports) {
			if (!im.equals(cm.classname)) {
				HashMap<String, String> tsImport = new HashMap<>();
				// TVG: This is used as class name in the import statements of the model file
				tsImport.put("classname", im);
				tsImport.put("filename", toModelFilename(removeModelPrefixSuffix(im)));
				tsImports.add(tsImport);
			}
		}
		return tsImports;
	}

	@Override
	public String toApiImport(String name) {
		if (importMapping.containsKey(name)) {
			return importMapping.get(name);
		}
		return apiPackage() + "/" + toApiFilename(name);
	}

	@Override
	public String toModelFilename(String name) {
		if (importMapping.containsKey(name)) {
			return importMapping.get(name);
		}
		return DEFAULT_IMPORT_PREFIX + camelize(this.sanitizeName(name), false);
	}

	@Override
	public String toModelImport(String name) {
		if (importMapping.containsKey(name)) {
			return importMapping.get(name);
		}
		return modelPackage() + "/" + toModelFilename(name).substring(DEFAULT_IMPORT_PREFIX.length());
	}

	public String getNpmRepository() {
		return npmRepository;
	}

	public void setNpmRepository(String npmRepository) {
		this.npmRepository = npmRepository;
	}

	public String removeModelPrefixSuffix(String name) {
		String result = name;
		String prefix = capitalize(this.modelNamePrefix);
		String suffix = capitalize(this.modelNameSuffix);
		if (prefix.length() > 0 && result.startsWith(prefix)) {
			result = result.substring(prefix.length());
		}
		if (suffix.length() > 0 && result.endsWith(suffix)) {
			result = result.substring(0, result.length() - suffix.length());
		}

		return result;
	}

	@Override
	public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
		try {
			objs.put("openAPIJson", Json.mapper().writeValueAsString(objs.get("openAPI")));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
		return objs;
	}

	static class ExtendedCodegenOperation extends CodegenOperation {
		public List<CodegenParameter> xParams = new ArrayList<>();
		public List<CodegenParameter> xRequestBodyParams = new ArrayList<>();

		public boolean isBodyParamsRequired;

		public ExtendedCodegenOperation(CodegenOperation other) {
			super(other);
			for (CodegenParameter param : allParams) {
				if (param.isBodyParam || param.isFormParam) {
					xRequestBodyParams.add(param);
				} else {
					xParams.add(param);
				}
			}
			isBodyParamsRequired = xRequestBodyParams.stream().anyMatch(codegenParameter -> codegenParameter.required);
		}
	}
}
