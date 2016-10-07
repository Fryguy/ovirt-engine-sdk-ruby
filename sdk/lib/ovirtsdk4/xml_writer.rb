#
# Copyright (c) 2016 Red Hat, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require 'stringio'

module OvirtSDK4
  class XmlWriter
    def initialize(io = nil, indent = nil)
      io ||= StringIO.new
      unless io.is_a?(StringIO)
        raise Error, "The type of the 'io' parameter must be 'IO', but it is '#{io.class}'"
      end
    end

    def close
    end

    def flush
    end

    def string
    end

    def write_attribute(name, value)
    end

    def write_element(name, value)
    end

    def write_end
    end

    def write_start(name)
    end
  end
end
