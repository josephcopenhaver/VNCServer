# runtime dependencies
require 'FileUtils'

# Build dependencies
repositories.remote << 'http://repo1.maven.org/maven2'

# build global settings
ENV['JAVA_OPTS'] ||= '-Xlint:unchecked'


IS_WINDOWS = [nil]
def antSyscall *args
	if IS_WINDOWS[0] == nil
		IS_WINDOWS[0] = (ENV['OS'] == 'Windows_NT')
	end
	if IS_WINDOWS[0] then
		args.map! do |v|
			# this at least works on jruby windows and Apache Ant(TM) version 1.8.4
			# not sure about real-ruby
			if /[,\*\s\t\v"\|\<\>\&\^%]/ =~ v
				#
				# %STR% tokens will not be expanded
				#
				v = ('"' + v.gsub('"', '""') + '"').gsub(/([\|\<\>\&\^])/, "^\\1").gsub('%', '%%')
			end
			v
		end
		#printf "cmd /c \"%s\"\n", args.join(" ")
	else
		assert("Why use jruby when you are not on windows?") {!(defined? JRUBY_VERSION)} # you can try commenting this out, but I never tested that
	end
	system(*args)
end

class AssertionError < RuntimeError
end

def assert *msg
	raise (msg.length == 0 ? AssertionError : (AssertionError.new *msg)) unless yield
end

def antTool *cmdArgs
	assert { antSyscall("ant", "-buildfile", "ant_tools.xml", *cmdArgs) }
end

def rm? file
	if File.file?(file)
		return FileUtils.rm(file)
	end
	false
end

def rm_r? dir
	if File.exists?(dir)
		return FileUtils.rm_r(dir)
	end
	false
end

def mkdir? dir
	if !Dir.exists?(dir)
		return FileUtils.mkdir(dir)
	end
	false
end



class SelectiveCompiler < Compiler::Base

# REFERENCE = http://svn.apache.org/repos/asf/buildr/tags/1.3.4/lib/buildr/core/compile.rb

	class << self
		alias_method :_applies_to_q, :applies_to?
		
		@@enabled = false
		
		def enabled=enabled
			@@enabled = enabled
		end
		
		def enabled
			@@enabled
		end
		
		def applies_to?(*args, &blk)
			if not @@enabled
				return false
			end
			_applies_to_q(*args, &blk)
		end
	end
	
	def compile(sources, target, dependencies)
		assert {sources.length == 1}
		src = sources[0]
		FileUtils.mkdir_p src
		target_version = "1.6"
		dst = target
		includes = @options[:includes]
		excludes = @options[:excludes]
		antTool("-Dtarget_version=#{target_version}", "-Dsrc=#{src}", "-Ddst=#{dst}", "-Dincludes=#{includes}", "-Dexcludes=#{excludes}")
	end
		
	def check_options(options, *supported)
		raise 'Not implemented'
	end
	
	def files_from_sources(sources)
		raise 'Not implemented'
	end

	def compile_map(sources, target)
		# 1. ensure bin dir state does NOT include
		#  any explicitly ignored (probably modal) files
		#  this would indicate a clean is most likely required
		excludes = nil
		excludes_bin_regex = @options[:excludes_bin_regex]
		if excludes_bin_regex == nil || !excludes_bin_regex.instance_of?(Regexp)
			# construct the bin regex
			excludes_bin_regex = nil
			excludes = @options[:excludes]
			if excludes != nil && excludes != ""
				excludes_bin_regex = excludes.split(',')
				excludes_bin_regex.map! do |v|
					if v.end_with? '/**/*.java'
						v = '^' + (Regexp.escape v[0..-10]) + '.*\.class$'
					elsif v.end_with? '.java'
						v = '^' + (Regexp.escape v[0..-6]) + '\.class$'
					end
					Regexp.new v
				end
				excludes_bin_regex = Regexp.union(excludes_bin_regex)
			end
			@options[:excludes_bin_regex] = excludes_bin_regex
		end
		if excludes_bin_regex != nil
			# apply the bin exclusion regex if defined
			FileList["#{target}/**/*"].each do |file|
				if (!File.directory?(file))
					relPath = Util.relative_path(file, target)
					if relPath =~ excludes_bin_regex
						raise (AssertionError.new "Clean task highly recommended before continuing: found file explicitly excluded in a compiled state, \"#{relPath}\"")
					end
				end
			end
		end
		#
		# when creating the file map,
		# explicitly exclude files not part of this modal/
		# selective file set build
		#
		excludes_regex = @options[:excludes_regex]
		if excludes_regex != nil && excludes_regex.instance_of?(Regexp)
			excludes = excludes_regex
		else
			if excludes == nil
				excludes = @options[:excludes]
			end
			if excludes != nil
				if excludes != ""
					excludes = excludes.split(',')
					excludes.map! do |v|
						if v.end_with? "/**/*.java"
							v = '^' + (Regexp.escape v[0..-10]) + '.*\.java$'
						else
							v = '^' + (Regexp.escape v) + '$'
						end
						Regexp.new v
					end
					excludes = Regexp.union(excludes)
				else
					excludes = nil
				end
				@options[:excludes_regex] = excludes
			end
		end
		#
		# Construct the possibly applicable filtered to-compile set of files
		#
		target_ext = self.class.target_ext
		ext_glob = Array(self.class.source_ext).join(',')
		sources.flatten.map{|f| File.expand_path(f)}.inject({}) do |map, source|
			if File.directory?(source)
				FileList["#{source}/**/*.{#{ext_glob}}"].reject { |file|
					File.directory?(file) || (excludes != nil && (Util.relative_path(file, source) =~ excludes))
				}.each { |file|
					map[file] = File.join(target, Util.relative_path(file, source).ext(target_ext))
				}
			else
				map[source] = target
			end
			map
		end
	end
end
SelectiveCompiler.specify :language=>:java, :target=>'classes', :target_ext=>'class'
Buildr::Compiler.add SelectiveCompiler



# export tasks to the project layer
Project.local_task :cleangit

SRC_JAVA_PATH = 'src'
OUTPUT_BIN_DIR = 'bin'

layout = Layout.new
layout[:source, :main, :java] = SRC_JAVA_PATH
layout[:target, :main, :classes] = OUTPUT_BIN_DIR

define 'JCOPE_VNC', :layout=>layout do
	
	compile.options.source = '1.6'
	
	bs = Buildr.settings.build
	_mode = bs['mode']
	mode = _mode
	native_support = bs['native_support']
	assert {[nil, true, false].include?(native_support)}
	includes = nil
	excludes = nil
	
	# filter config set
	assert {[nil, "client", "server"].include?(mode)}
	if nil == mode
		mode = "client"
		compile.using(:javac)
	else
		compile.using(:selectivecompiler)
		SelectiveCompiler.enabled = true
		if mode == "client"
			includes = "com/jcope/vnc/Client.java"
			excludes = "com/jcope/vnc/server/**/*.java,com/jcope/vnc/Server.java,com/jcope/vnc/ServerSetup.java"
			if native_support
				includes += ",com/jcope/vnc/client/NativeDecorator.java"
			end
		else
			includes = "com/jcope/vnc/Server.java,com/jcope/vnc/ServerSetup.java"
			excludes = "com/jcope/vnc/client/**/*.java,com/jcope/vnc/Client.java"
		end
		options = compile.options
		options.includes = includes
		options.excludes = excludes
	end
	
	# echo config
	printf "\n\nCONFIG:\n\n"
	#
	printf "OPTIONS:\n"
	printf "mode=%s\n", _mode
	printf "native_support=%s\n", native_support
	printf "\nRESULTANTS:\n"
	printf "mode=%s\n", mode
	printf "includes=%s\n", includes
	printf "excludes=%s\n", excludes
	#
	printf "\n:CONFIG\n\n\n"
	
	# change modal files using minimalistic ant command
	mode_cache_ref_type = (mode == "client") ? "Soft" : "Weak"
	antTool("regexp_replace", "-Dfile=#{SRC_JAVA_PATH}/com/jcope/util/BufferPool.java", "-Dmatch=(?:Weak|Soft)Reference", "-Dreplace=#{mode_cache_ref_type}Reference")
	
	clean do
		rm_r?(OUTPUT_BIN_DIR)
	end
	
	task :cleangit => :clean do
		mkdir?(OUTPUT_BIN_DIR)
		FileUtils.touch("#{OUTPUT_BIN_DIR}/empty")
		rm_r?('build.log')
		['setup.log', 'client.log', 'server.log', 'server.lock', 'server.pid'].each do |file|
			rm?(file)
		end
	end
end