package com.condation.cms.modules.thymeleaf;

/*-
 * #%L
 * cms-server
 * %%
 * Copyright (C) 2023 Marx-Software
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.condation.cms.api.ServerProperties;
import com.condation.cms.api.db.DBFileSystem;
import com.condation.cms.api.template.TemplateEngine;
import com.condation.cms.api.theme.Theme;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 *
 * @author thmar
 */

public class ThymeleafTemplateEngine implements TemplateEngine {

	private org.thymeleaf.TemplateEngine htmlEngine;
	private org.thymeleaf.TemplateEngine jsEngine;
	private org.thymeleaf.TemplateEngine stringEngine;
	private final ServerProperties serverProperties;
	private final DBFileSystem fileSystem;
	private Theme theme;

	public ThymeleafTemplateEngine(final DBFileSystem fileSystem, 
			final ServerProperties serverProperties,
			final Theme theme) {
	
		
		this.serverProperties = serverProperties;
		this.fileSystem = fileSystem;
		this.theme = theme;
		
		initHtmlTemplateing();
		initJSTemplateing();
		initStringTemplateing();
	}
	
	private void initTemplateResolver (TemplateMode mode, org.thymeleaf.TemplateEngine templateEngine) {
		var templateBase = fileSystem.resolve("templates/");
		ITemplateResolver siteTemplateResolver = templateResolver(templateBase, mode);
		ITemplateResolver themeTemplateResolver = null;
		ITemplateResolver parentThemeTemplateResolver = null;
		if (!theme.empty()) {
			themeTemplateResolver = templateResolver(theme.templatesPath(), mode);
		}
		if (!theme.empty() && theme.getParentTheme() != null) {
			parentThemeTemplateResolver = templateResolver(theme.getParentTheme().templatesPath(), mode);
		}

		templateEngine.setTemplateResolver(
				new ThemeTemplateResolver(
						siteTemplateResolver, 
						Optional.ofNullable(themeTemplateResolver),
						Optional.ofNullable(parentThemeTemplateResolver)
				)
		);
	}
	
	private void initHtmlTemplateing () {
		htmlEngine = new org.thymeleaf.TemplateEngine();
		initTemplateResolver(TemplateMode.HTML, htmlEngine);
	}
	
	private void initStringTemplateing () {
		stringEngine = new org.thymeleaf.TemplateEngine();
		stringEngine.setTemplateResolver(new StringTemplateResolver());
	}
	
	private void initJSTemplateing () {
		jsEngine = new org.thymeleaf.TemplateEngine();
		initTemplateResolver(TemplateMode.JAVASCRIPT, jsEngine);
	}
	
	private ITemplateResolver templateResolver (final Path templatePath, final TemplateMode mode) {
		var templateResolver = new FileTemplateResolver();
		templateResolver.setTemplateMode(mode);
		templateResolver.setPrefix(templatePath.toString() + File.separatorChar);
		//templateResolver.setSuffix(".html");
		if (serverProperties.dev()) {
			templateResolver.setCacheable(false);
		} else {
			templateResolver.setCacheable(true);
			templateResolver.setCacheTTLMs(TimeUnit.MINUTES.toMillis(1));
		}
		
		return templateResolver;
	}

	@Override
	public String render(String template, TemplateEngine.Model model) throws IOException {

		Writer writer = new StringWriter();
		final Context context = new Context(Locale.getDefault(), model.values);
		
		if (model.contentNode != null && "application/json".equals(model.contentNode.contentType())) {
			jsEngine.process(template, context, writer);
		} else {
			htmlEngine.process(template, context, writer);
		}		
		
		return writer.toString();
	}

	@Override
	public String renderFromString(String templateString, Model model) throws IOException {
		final Context context = new Context(Locale.getDefault(), model.values);
		return stringEngine.process(templateString, context);
	}
	
	

	@Override
	public void invalidateCache() {
		htmlEngine.getCacheManager().clearAllCaches();
	}

	@Override
	public void updateTheme(Theme theme) {
		this.theme = theme;
		
		initHtmlTemplateing();
		initJSTemplateing();
	}

}
