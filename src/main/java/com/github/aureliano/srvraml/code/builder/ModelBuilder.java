package com.github.aureliano.srvraml.code.builder;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.github.aureliano.srvraml.code.meta.ClassMeta;
import com.github.aureliano.srvraml.code.meta.FieldMeta;
import com.github.aureliano.srvraml.code.meta.MethodMeta;
import com.github.aureliano.srvraml.helper.CodeBuilderHelper;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;

public class ModelBuilder implements IBuilder {

	private ClassMeta clazz;
	private static final Set<String> GENERATED_CLASSES = new HashSet<String>();
	
	protected ModelBuilder() {
		super();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public ModelBuilder parse(String pkg, String entity, Object resource) {
		Map<?, ?> map = this.parseJsonString(resource.toString());
		Map<String, Map<String, String>> properties = (Map<String, Map<String, String>>) map.get("properties");
		
		String javaDoc = new StringBuilder()
			.append("Generated by srvraml-maven-plugin.")
			.append("\n\n")
			.append(map.get("description"))
			.toString();
		
		this.clazz = new ClassMeta()
			.withPackageName(pkg + ".model")
			.withJavaDoc(javaDoc)
			.withClassName(StringUtils.capitalize(entity));
		
		if (GENERATED_CLASSES.contains(this.clazz.getCanonicalClassName())) {
			throw new IllegalArgumentException("Class " + this.clazz.getCanonicalClassName() + " was already generated before. Skipping!");
		}
		
		for (String fieldName : properties.keySet()) {
			Map<String, String> property = properties.get(fieldName);
			property.put("name", fieldName);
			FieldMeta attribute = FieldMeta.parse(property);
			
			this.clazz
				.addField(attribute)
				.addMethod(CodeBuilderHelper.createGetterMethod(attribute))
				.addMethod(CodeBuilderHelper.createSetterMethod(attribute))
				.addMethod(CodeBuilderHelper.createBuilderMethod(this.clazz.getClassName(), attribute));
		}

		GENERATED_CLASSES.add(this.clazz.getCanonicalClassName());
		return this;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public ModelBuilder build() {
		this.buildJavaClass();
		return this;
	}
	
	private void buildJavaClass() {
		try {
			JCodeModel codeModel = new JCodeModel();
			JDefinedClass definedClass = codeModel._class(this.clazz.getCanonicalClassName());
			definedClass.javadoc().append(this.clazz.getJavaDoc());
			
			this.appendClassAttributes(codeModel, definedClass);
			this.appendClassMethods(codeModel, definedClass);
			
			codeModel.build(new File("src/main/java"));
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	private void appendClassMethods(JCodeModel codeModel, JDefinedClass definedClass) {
		for (MethodMeta method : this.clazz.getMethods()) {
			CodeBuilderHelper.addMethodToClass(codeModel, definedClass, method);
		}
	}

	private void appendClassAttributes(JCodeModel codeModel, JDefinedClass definedClass) {
		for (FieldMeta field : this.clazz.getFields()) {
			CodeBuilderHelper.addAttributeToClass(codeModel, definedClass, field);
		}
	}

	private Map<?, ?> parseJsonString(String json) {
		try {
			return OBJECT_MAPPER.readValue(json, HashMap.class);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public ClassMeta getClazz() {
		return clazz;
	}
	
	public ModelBuilder withClazz(ClassMeta clazz) {
		this.clazz = clazz;
		return this;
	}
}