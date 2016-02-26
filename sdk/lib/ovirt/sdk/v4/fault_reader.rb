#--
# Copyright (c) 2016 Red Hat, Inc.
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
#++

module Ovirt
  module SDK
    module V4

      class FaultReader < Reader # :nodoc:

        def self.read_one(reader)
          # Do nothing if there aren't more tags:
          return nil unless reader.forward

          # Create the object:
          fault = Fault.new

          # Discard the start tag:
          empty = reader.empty_element?
          reader.read
          return fault if empty

          # Process the inner elements:
          while reader.forward do
            case reader.node_name
            when 'reason'
              fault.reason = reader.read_string
              reader.next_element
            when 'detail'
              fault.detail = reader.read_string
              reader.next_element
            else
              reader.next_element
            end
          end

          # Discard the end tag:
          reader.read

          return fault
        end

      end

    end
  end
end