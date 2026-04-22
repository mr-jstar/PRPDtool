function [q_tm, q] = importPDData(folder, qUnit);

fileName = sprintf('%s\\%s.PD', folder, qUnit);

file = fopen(fileName, 'rb');

if file == -1
   msg = sprintf('file %s could not be opened', fileName);
   error(msg);
end

fseek(file, 0, 'bof');
q = fread(file, inf, 'float32', 8);
fseek(file, 4, 'bof');
q_tm = fread(file, inf, 'float64', 4);

fclose(file);
