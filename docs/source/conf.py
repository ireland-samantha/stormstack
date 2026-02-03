# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

# -- Project information -----------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#project-information

project = 'StormStack'
copyright = '2026, Samantha Ireland'
author = 'Samantha Ireland'
release = '2.0.0'

# -- General configuration ---------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#general-configuration

extensions = [
    'sphinx.ext.autodoc',
    'sphinx.ext.viewcode',
    'sphinx.ext.intersphinx',
    'sphinx.ext.todo',
]

templates_path = ['_templates']
exclude_patterns = []

# The master toctree document.
master_doc = 'index'

# -- Options for HTML output -------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#options-for-html-output

html_theme = 'sphinx_rtd_theme'
html_static_path = ['_static']

# Theme options
html_theme_options = {
    'navigation_depth': 4,
    'collapse_navigation': False,
    'sticky_navigation': True,
    'includehidden': True,
    'titles_only': False,
}

# -- Options for intersphinx extension ---------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#intersphinx-configuration

intersphinx_mapping = {
    'python': ('https://docs.python.org/3', None),
}

# -- Options for todo extension ----------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#todo-configuration

todo_include_todos = True

# -- Custom configuration ----------------------------------------------------

# Add any custom CSS
html_css_files = [
    'custom.css',
]

# Logo and favicon
# html_logo = '_static/logo.png'
# html_favicon = '_static/favicon.ico'

# Show "Edit on GitHub" links
html_context = {
    'display_github': True,
    'github_user': 'samanthaireland',
    'github_repo': 'stormstack',
    'github_version': 'main',
    'conf_py_path': '/docs/source/',
}
