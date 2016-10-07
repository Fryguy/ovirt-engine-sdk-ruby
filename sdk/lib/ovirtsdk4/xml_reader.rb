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

require 'nokogiri'

module OvirtSDK4
  class XmlReader
    def initialize(io)
      unless io.is_a?(String) || io.is_a?(IO)
        raise Error, "The type of the 'io' parameter must be 'String' or 'IO', but it is '#{io.class}'"
      end

      @reader = Nokogiri::XML::Reader(io)
      read
    end

    def forward
      loop do
        case node_type
        when Nokogiri::XML::Reader::TYPE_ELEMENT
          return true
        when Nokogiri::XML::Reader::TYPE_END_ELEMENT, Nokogiri::XML::Reader::TYPE_NONE
          return false
        else
          read
        end
      end
    end

    def read
      !!@reader.read
    rescue
      raise Error, "Can't move to next node"
    end

    def node_name
      @reader.name
    end

    def empty_element?
      @reader.empty_element?
    rescue
      raise Error, "Can't check if current element is empty"
    end

    def get_attribute(name)
      @reader.attribute(name)
    end

    def read_element
      if node_type != Nokogiri::XML::Reader::TYPE_ELEMENT
        raise Error, "Current node isn't the start of an element"
      end

      # For empty values elements there is no need to read the value. For non empty values we need to read the value, and
      # check if it is nil, as that means that the value is an empty string.
      value =
        if empty_element?
          nil
        else
          @reader.value || ""
        end

      next_element
      value
    end

    def read_elements
      # This method assumes that the reader is positioned at the element that contains the values to read. For example
      # if the XML document is the following:
      #
      # <list>
      #   <value>first</value>
      #   <value>second</value>
      # </list>
      #
      # The reader should be positioned at the <list> element. The first thing we need to do is to check:
      if node_type != Nokogiri::XML::Reader::TYPE_ELEMENT
        raise Error, "Current node isn't the start of an element"
      end

      # If we are indeed positioned at the first element, then we need to check if it is empty, <list/>, as we will
      # need this lter, after discarding the element:
      empty = empty_element?

      # Now we need to discard the current element, as we are interested only in the nested <value>...</value>
      # elements:
      read

      # Create the list that will contain the result:
      list = []

      # At this point, if the start element was empty, we don't need to do anything else:
      return list if empty

      # Process the nested elements:
      loop do
        case node_type
        when Nokogiri::XML::Reader::TYPE_ELEMENT
          list << read_element
        when Nokogiri::XML::Reader::TYPE_END_ELEMENT, Nokogiri::XML::Reader::TYPE_NONE
          break
        else
          next_element
        end
      end

      # Once all the nested <value>...</value> elements are processed the reader will be positioned at the closing
      # </list> element, or at the end of the document. If it is the closing element then we need to discard it.
      read if node_type == Nokogiri::XML::Reader::TYPE_END_ELEMENT

      list
    end

    def next_element
      unless @reader.self_closing?
        depth = @reader.depth
        read until @reader.depth == depth && @reader.node_type == Nokogiri::XML::Reader::TYPE_END_ELEMENT
      end
      read
    end

    def close
    end

    private

    def node_type
      type = @reader.node_type
      raise Error, "Can't get current node type" if type == -1
      type
    end
  end
end
