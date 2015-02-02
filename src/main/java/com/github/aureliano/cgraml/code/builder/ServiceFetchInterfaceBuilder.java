package com.github.aureliano.cgraml.code.builder;

import java.io.File;

import com.github.aureliano.cgraml.code.gen.ServiceFetchInterfaceGenerator;
import com.github.aureliano.cgraml.code.meta.ClassMeta;
import com.github.aureliano.cgraml.code.meta.FieldMeta;
import com.github.aureliano.cgraml.code.meta.MethodMeta;
import com.github.aureliano.cgraml.code.meta.Visibility;
import com.github.aureliano.cgraml.helper.CodeBuilderHelper;
import com.sun.codemodel.ClassType;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;


public class ServiceFetchInterfaceBuilder implements IBuilder {

	private ClassMeta clazz;
	private static final MethodMeta[] ABSTRACT_METHODS;
	
	static {
		ABSTRACT_METHODS = new MethodMeta[] {
			createWithParametersMethod(),
			createHttpGetMethod(),
			createGetParametersMethod()
		};
	}

	public ServiceFetchInterfaceBuilder() {
		super();
	}

	@SuppressWarnings("unchecked")
	@Override
	public ServiceFetchInterfaceBuilder parse(String pkg, String entity, Object resource) {
		String javaDoc = "Generated by srvraml-maven-plugin.\n\nDefine a type for API service fetch data.";
		
		this.clazz = new ClassMeta()
			.withPackageName(pkg)
			.withJavaDoc(javaDoc)
			.withClassName(entity);
		
		for (MethodMeta method : ABSTRACT_METHODS) {
			this.clazz.addMethod(method);
		}
		
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ServiceFetchInterfaceBuilder build() {
		this.buildJavaClass();
		return this;
	}
	
	private void buildJavaClass() {
		try {
			JCodeModel codeModel = new JCodeModel();
			JDefinedClass definedClass = codeModel._class(this.clazz.getCanonicalClassName(), ClassType.INTERFACE);
			definedClass.javadoc().append(this.clazz.getJavaDoc());
			
			codeModel.ref(this.clazz.getPackageName().replace(".service", ".model.ICollectionModel"));
			definedClass.generify("T extends ICollectionModel<?>");
			
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

	private static MethodMeta createWithParametersMethod() {
		MethodMeta method = new MethodMeta();
		
		method.setName("withParameters");
		method.setVisibility(Visibility.PUBLIC);
		method.setReturnType(ServiceFetchInterfaceGenerator.CLASS_NAME);
		method.setGenericReturnType("T");
		
		FieldMeta param = new FieldMeta();
		param.setName("parameters");
		param.setType(ServiceFetchInterfaceGenerator.PACKAGE_NAME.replace(".service", ".parameters.IServiceParameters"));
		
		method.addParameter(param);
		
		return method;
	}

	private static MethodMeta createHttpGetMethod() {
		MethodMeta method = new MethodMeta();
		
		method.setName("httpGet");
		method.setVisibility(Visibility.PUBLIC);
		method.setReturnType("T");
		
		return method;
	}

	private static MethodMeta createGetParametersMethod() {
		MethodMeta method = new MethodMeta();
		
		method.setName("getParameters");
		method.setVisibility(Visibility.PUBLIC);
		method.setReturnType("IServiceParameters");
		
		return method;
	}
	
	public static MethodMeta[] getAbstractMethods() {
		return ABSTRACT_METHODS;
	}
	
	public ClassMeta getClazz() {
		return clazz;
	}
	
	public ServiceFetchInterfaceBuilder withClazz(ClassMeta clazz) {
		this.clazz = clazz;
		return this;
	}
}