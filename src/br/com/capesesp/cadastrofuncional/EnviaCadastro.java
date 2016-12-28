package br.com.capesesp.cadastrofuncional;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.JobExecutionContext;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import br.com.capesesp.cadastrofuncional.domain.AssociadoAtualizado;
import br.com.capesesp.cadastrofuncional.domain.AssociadoAtualizadoLista;
import br.com.capesesp.cadastrofuncional.domain.DadosCadastrais;
import br.com.capesesp.enviodesenhas.EnviaSenhasConstantes;
import br.com.capesesp.opus.OpusAdapter;
import br.com.capesesp.opus.OpusException;
import br.com.capesesp.ws.GenericSaida;
import br.com.capesesp.ws.StatusExecucao;
import https.ws_beneficiario_funcionalcard_com.Retorno;

public class EnviaCadastro implements CadastroFuncionalConstantes{

	public static final Logger logger = Logger.getLogger("br.com.capesesp.funcional");

	public EnviaCadastro() {}
	
	public void executa(JobExecutionContext context, String nomeJob, String nomeTrigger) {
		
		Gson gson = new Gson();
		String retornoJsonSistemaCentral = "";
		Retorno retornoFuncional = new Retorno();
		CadastroFuncional enviaCadastroFuncional = new CadastroFuncional();
		Map<String, String> dataHora = formatarDataHora();
		Integer problemaFuncional = 0;
		Integer problemaSistemaCentral = 0;
		
		try {
			
			retornoJsonSistemaCentral = OpusAdapter.fucj03();
			AssociadoAtualizadoLista listaAssociados = gson.fromJson(retornoJsonSistemaCentral, AssociadoAtualizadoLista.class);

			if (listaAssociados.statusExecucao.executadoCorretamente == true) {
				logger.log(Level.INFO, "Encontrados " + listaAssociados.associados.size() + " associados para envio do cadastro para a Funcional");
				
				for (AssociadoAtualizado associadoEnvio : listaAssociados.associados) {
					
					String matriculaSequencial = associadoEnvio.matricula + "-" + associadoEnvio.sequencial;
					
					try {
						retornoJsonSistemaCentral = OpusAdapter.fucj04(associadoEnvio.matricula,associadoEnvio.sequencial);
						
						DadosCadastrais dadosCadastrais = gson.fromJson(retornoJsonSistemaCentral, DadosCadastrais.class);

						if(dadosCadastrais.statusExecucao.mensagens.mensagem[0].codigo != 3){
							
							retornoFuncional = enviaCadastroFuncional.cadastrar(dadosCadastrais);
							
							if(retornoFuncional.getStatus() == 0){
								logger.info("Associado " + retornoFuncional.getMatricula() + " - " + retornoFuncional.getNumdep() + ", foi salvo corretamente na Funcional Card. Mensagem de retorno: " + retornoFuncional.getMensagem());
															
								retornoJsonSistemaCentral = OpusAdapter.fucj05(
										retornoFuncional.getMatricula(),
										retornoFuncional.getNumdep(), 
										dataHora.get("data"), 
										dataHora.get("hora"));
								
								GenericSaida genericSaida = gson.fromJson(retornoJsonSistemaCentral, GenericSaida.class);
								StatusExecucao statusExecucao = genericSaida.getStatusExecucao();
								
								if(statusExecucao.mensagens.mensagem[0].codigo == 7){
									logger.info("Associado " + retornoFuncional.getMatricula() + " - " + retornoFuncional.getNumdep() + ", foi salvo corretamente no Sistema Central");
								}else{
									logger.log(Level.SEVERE, "Não foi possivel salvar data e hora de envio no Sistema Central: " + statusExecucao.mensagens.mensagem[0].mensagem + " : " + retornoJsonSistemaCentral);
									problemaSistemaCentral++;
								}	
							}else{
								logger.info("Associado " + retornoFuncional.getMatricula() + " - " + retornoFuncional.getNumdep() + ", não foi atualizado na Funcional Card. Mensagem de retorno: " + retornoFuncional.getMensagem());
								problemaFuncional++;
							}
							
						}else{
							logger.info("Associado " + retornoFuncional.getMatricula() + " - " + retornoFuncional.getNumdep() + ", não foi atualizado na Funcional Card. Mensagem de retorno: " + retornoFuncional.getMensagem());
							problemaSistemaCentral++;
							
						}
						
						
					}catch (JsonSyntaxException e){
						logger.log(Level.SEVERE, "Erro na estrutura Json para o associado " + matriculaSequencial +" : " + e.getMessage(), e);
					}catch (OpusException e){
						logger.log(Level.SEVERE, "Erro ao executar o programa opus: " + e.getMessage(), e);
					}catch(Exception e){
						logger.log(Level.SEVERE, "Erro interno: " + e.getMessage(), e);
					}
					
				}
			}else{
				logger.log(Level.SEVERE, "Erro ao executar o programa fucj03");
				Thread.sleep(INTERVALO_ERRO);
			}

		} catch (Exception e) {
			try {
				Thread.sleep(EnviaSenhasConstantes.INTERVALO_ERRO);
			} catch (InterruptedException e1) {
				logger.log(Level.SEVERE, "Erro ao colocar a Thread em Sleep: " + e1.getMessage(), e1);
			}
		}
		
		logger.info("Problemas de envio para a funcional: " + problemaFuncional);
		logger.info("Problemas Sistema Central: " + problemaSistemaCentral);
		logger.info("Total de Problemas : " + (problemaSistemaCentral + problemaFuncional));

		
	}
	
	/**
	 * Metodo responsavel por formatar a data e hora para ser incluida no Sistema Central
	 * @return
	 */
	public Map<String,String> formatarDataHora(){
		
		Calendar calendar = new GregorianCalendar();
		SimpleDateFormat formatoData = new SimpleDateFormat("yyyyMMdd");
		SimpleDateFormat formatoHora = new SimpleDateFormat("kk:mm:ss");
		
		Date date = new Date();
		calendar.setTime(date);
		
		String data = formatoData.format(calendar.getTime());
		String hora = formatoHora.format(calendar.getTime());
		
		Map<String, String> dataHora = new HashMap<String, String>();
		dataHora.put("data" , data);
		dataHora.put("hora" , hora);
		
				
		return dataHora;
	}
	
}
