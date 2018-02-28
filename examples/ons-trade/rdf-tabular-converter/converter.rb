require 'rubygems'
require 'rdf/tabular'
require 'rdf/turtle'

=begin
RDF::Writer.open("./doap.ttl") do |writer|
  RDF::Tabular::Reader.open("./doap.csv", metadata: "./doap.csv-metadata.json", minimal: true).each do |statement|
    writer << statement
  end
end
=end

RDF::Writer.open("regional-trade.ttl") do |writer|
  RDF::Tabular::Reader.open("regional-trade.prepared.csv", minimal: true).each do |statement| #  metadata: "regional-trade.prepared.csv-metadata.json"
    writer << statement
  end
end
